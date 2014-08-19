/*
 * Decentralized Information Flow Control LSM module
 *
 * Copyright (C) 2008 Don Porter <porterde@cs.utexas.edu>
 *
 * Implements DIFC similar to Flume as an LSM module.
 *
 */

#include <linux/module.h>
#include <linux/kernel.h>
#include <linux/init.h>
#include <linux/security.h>
#include <linux/xattr.h>
#include <linux/sched.h>
#include <asm/uaccess.h>
#include <linux/proc_fs.h>
#include <linux/rwsem.h>

/* should we print out debug messages */
static int debug = 0;

/* Proc file accessors for difc_debug */
struct proc_dir_entry *difc_debug_procfile;
#define PROC_BUF_SIZE 64
static char proc_buffer[PROC_BUF_SIZE];

int difc_debug_procfile_read(char *buffer,
			     char **buffer_location,
			     off_t offset, int buffer_length, int *eof, void *data){

	int ret = sprintf(buffer, "%d\n", debug);
	return ret;
}

int difc_debug_procfile_write(struct file *file,
			      const char __user *buffer,
			      unsigned long count, void *data){
	
	/* get buffer size */
	int procfs_buffer_size = count;
	if (procfs_buffer_size > PROC_BUF_SIZE ) {
		procfs_buffer_size = PROC_BUF_SIZE;
	}
	
	/* write data to the buffer */
	if ( copy_from_user(proc_buffer, buffer, procfs_buffer_size) ) {
		return -EFAULT;
	}
	
	sscanf(proc_buffer, "%d", &debug);
	return procfs_buffer_size;
}


/* flag to keep track of how we were registered */
static int secondary;

module_param(debug, bool, 0600);
MODULE_PARM_DESC(debug, "Debug enabled or not");

#if defined(CONFIG_SECURITY_DIFC_MODULE)
#define MY_NAME THIS_MODULE->name
#else
#define MY_NAME "difc"
#endif

#define XATTR_DIFC_SUFFIX "difc"
#define XATTR_NAME_DIFC XATTR_SECURITY_PREFIX XATTR_DIFC_SUFFIX

#define difc_debug(fmt, arg...)					\
	do {							\
		if (debug)					\
			printk(KERN_ERR "(%d) %s: %s: " fmt ,	\
			       current->pid, MY_NAME , __FUNCTION__ , 	\
				## arg);			\
	} while (0)




/* A label list is a label* where the first value is the length. */
typedef label_t* labelList_t;

/* Arbitrarily choose a list buffer size*/
#define LABEL_LIST_BYTES 256
#define LABEL_LIST_LABELS (LABEL_LIST_BYTES / sizeof(label_t))
#define LABEL_LIST_MAX_ENTRIES (LABEL_LIST_BYTES / sizeof(label_t)) - 1 

/* Capability stuff */
atomic_t cur_max_capability;

/* Reserve 0 label for TCB */
#define LABEL_TCB     0
#define CAP_START_VAL 1

/* Use the upper two bits for +/- */
#define CAP_PLUS_MASK  (1<<30)
#define CAP_MINUS_MASK (1<<31)
#define CAP_MAX_VAL    (1<<29)
#define CAP_LABEL_MASK (0xFFFFFFFF ^ (CAP_PLUS_MASK | CAP_MINUS_MASK))

typedef uint64_t capability_t;
typedef capability_t* capList_t;

#define CAP_LIST_BYTES 256
#define CAP_LIST_CAPS (LABEL_LIST_BYTES / sizeof(capability_t))
#define CAP_LIST_MAX_ENTRIES (CAP_LIST_BYTES / sizeof(capability_t)) - 1


struct label_struct {
	label_t secList[LABEL_LIST_LABELS];
	label_t intList[LABEL_LIST_LABELS];
};

struct cap_segment{
	struct list_head list;
	capability_t caps[CAP_LIST_CAPS];
};

/* Task security struct */
struct task_security_struct {
	struct label_struct label;
	struct list_head capList;
	struct list_head capSuspendList;
	//capability_t capList[CAP_LIST_CAPS];
	//capability_t capSuspendList[CAP_LIST_CAPS];
	spinlock_t cap_lock; /* Only to lock capabilities.  Labels are private */
	int tcb;  /* Part of TCB. */
};

struct object_security_struct {
	struct label_struct label;
	struct rw_semaphore label_change_sem; /* Protects against races on label changes */
};

/* Cache for the security structs */
static struct kmem_cache *difc_task_cachep;
static struct kmem_cache *difc_object_cachep;
static struct kmem_cache *difc_caps_cachep;

#define alloc_cap_segment() kmem_cache_zalloc(difc_caps_cachep, GFP_KERNEL)
#define free_cap_segment(s) kmem_cache_free(difc_caps_cachep, s)

/* Nice, clean labellist iterator */
#define list_for_each_label(idx, l, head)	\
	for(idx = 1; idx <= *(head) && ({l = head[idx]; 1; }); idx++)

/* Nice, clean caplist iterator */
#define list_for_each_cap(idx, l, n, head)				\
	list_for_each_entry(n, &(head), list)				\
	for(idx = 1; idx <= (n)->caps[0] && ({l = (n)->caps[idx]; 1; }); idx++)

/* 
 * Common label-checking logic
 *
 * returns -EPERM on error, 0 is ok
 */
static int check_allowed(struct label_struct *src, struct label_struct *dest){

	label_t src_idx, src_label, dst_idx, dst_label;

	/* check secrecy constraint */
	if(src != NULL){
		list_for_each_label(src_idx, src_label, src->secList){
			int ok = 0;
			list_for_each_label(dst_idx, dst_label, dest->secList){
				if(src_label == dst_label){
					ok = 1;
					break;
				}
			}
			if(!ok){
				difc_debug("Failed secrecy on label %llu\n", src_label);
				return -EPERM;
			}
		}
	}
	
	/* check integrity constraint */
	if(dest != NULL){
		list_for_each_label(dst_idx, dst_label, dest->intList){
			int ok = 0;
			list_for_each_label(src_idx, src_label, src->intList){
				if(src_label == dst_label){
					ok = 1;
					break;
				}
			}
			if(!ok){
				difc_debug("Failed integrity on label %llu\n", dst_label);
				return -EPERM;
			}
		}
	}

	return 0;
}

static inline capability_t task_find_capability(struct task_security_struct *tsec, label_t label){

	capability_t idx, cap;
	struct cap_segment *n;
	list_for_each_cap(idx, cap, n, tsec->capList)
		if((cap & CAP_LABEL_MASK) == label)
			return cap;

	return 0;
}

#define LABEL_OP_ADD     0
#define LABEL_OP_REMOVE  1
#define LABEL_OP_REPLACE 2

#define LABEL_TYPE_SEC  0
#define LABEL_TYPE_INT  1

static inline int add_label(struct label_struct *lbl, label_t label, int label_type){
	label_t idx, l;
	labelList_t list;
	difc_debug("Attempting to add %llu to the labels\n", label);
	switch(label_type){
	case LABEL_TYPE_SEC: list = lbl->secList; break;
	case LABEL_TYPE_INT: list = lbl->intList; break;
	default: 
	  difc_debug("Invalid label\n");
	  return -EINVAL;
	}
	/* Check that we don't already have the label*/
	list_for_each_label(idx, l, list)
	  if(label == l){
	    difc_debug("Label already exists\n");
			return -EEXIST;
	  }

	if((*list) == LABEL_LIST_MAX_ENTRIES){
	  difc_debug("Nomem\n");
		return -ENOMEM;
	}

	difc_debug("Actually adding the label to the list\n");

	/* The label isn't present, add it */
	list[++(*list)] = label;
	return 0;
}

static inline int remove_label(struct label_struct *lbl, label_t label, int label_type){
	label_t idx, l;
	labelList_t list;
	difc_debug("Attempting to remove %llu from the labels\n", label);
	switch(label_type){
	case LABEL_TYPE_SEC: list = lbl->secList; break;
	case LABEL_TYPE_INT: list = lbl->intList; break;
	default: 
	  difc_debug("Invalid label\n");
	  return -EINVAL;
	}
	/* Find the label */
	list_for_each_label(idx, l, list)
		if(label == l)
			break;
	
	/* We didn't find it */
	if(idx > (*list)){
	  	  difc_debug("Label doesn't exist\n");
		return -ENOENT;
	}

	/* Shift everyone down one, writing over the label */
	while(idx < (*list)){
		list[idx] = list[idx+1];
		idx++;
	}

	/* Decrement the list count */
	(*list)--;
	return 0;
}

static int __difc_set_label(struct task_struct *tsk, struct label_struct *lbl, label_t label, int op_type, int label_type, int check_only){

	capability_t cap;

	/* Check that we have a security struct allocated */
	struct task_security_struct *tsec = tsk->security;
	if(!tsec){
	  	  difc_debug("Task has no security struct\n");
		return -ENOTTY; /* Just to be ridiculous... */
	}

	/* Check that the task has said permission */
	spin_lock(&tsec->cap_lock);
	cap = task_find_capability(tsec, label);
	spin_unlock(&tsec->cap_lock);

	if(!cap){
		difc_debug("Failed to find cap for %llu\n", label);
		return -EPERM;
	}

	difc_debug("Found cap %llu\n", cap);

	if(op_type == LABEL_OP_ADD){
		if((cap & CAP_PLUS_MASK)){
			return check_only ? 0 :  add_label(lbl, label, label_type);
		} else  {
			difc_debug("Missing plus mask for label %llu, cap %llu\n", label, cap);
			return -EPERM;
		}

	} else if(op_type == LABEL_OP_REMOVE){

		if((cap & CAP_MINUS_MASK)){
			return check_only ? 0 : remove_label(lbl, label, label_type);
		} else {
			difc_debug("Missing minus mask for label %llu, cap %llu\n", label, cap);
			return -EPERM;
		}

	} else {
	        difc_debug("Invalid op type\n");
		return -EINVAL;
	}


}

/* Logic to test if a bulk label replacement is ok */

static int check_label_replace(struct task_struct *tsk, struct label_struct *oldlabel, struct label_struct *newlabel){
	/* Check each addition _and_ each removal for proper caps */
	int rv;
	label_t src_idx, src_label, dst_idx, dst_label;

	/* check secrecy constraint */
	/* First check and adds */
	list_for_each_label(src_idx, src_label, newlabel->secList){
		int ok = 0;
		/* Only root can set TCB label */
		if(src_label == LABEL_TCB
		   && current->uid != 0){
		        difc_debug("Attempt by non-root to set TCB label\n");
			return -EPERM;
		}
			

		list_for_each_label(dst_idx, dst_label, oldlabel->secList){
			if(src_label == dst_label){
				ok = 1;
				break;
			}
		}
		if(!ok){
			if((rv = __difc_set_label(tsk, oldlabel, src_label, LABEL_OP_ADD, LABEL_TYPE_SEC, 1)) < 0){
				difc_debug("Failed to add secrecy label %llu\n", src_label);
				return rv;
			}
		}
	}

	/* Then removes */
	list_for_each_label(src_idx, src_label, oldlabel->secList){
		int ok = 0;
		list_for_each_label(dst_idx, dst_label, newlabel->secList){
			if(src_label == dst_label){
				ok = 1;
				break;
			}
		}
		if(!ok){
			if((rv = __difc_set_label(tsk, oldlabel, src_label, LABEL_OP_REMOVE, LABEL_TYPE_SEC, 1)) < 0){
				difc_debug("Failed to drop secrecy label %llu\n", src_label);
				return rv;
			}
		}
	}


	/* repeat for integrity constraint */
	/* First check and adds */
	list_for_each_label(src_idx, src_label, newlabel->intList){
		int ok = 0;

		/* Only root can set TCB label */
		if(src_label == LABEL_TCB){
			if(current->uid != 0){
				difc_debug("Failed to add TCB integrity label\n");
				return -EPERM;
			} else
				break;
		}

		list_for_each_label(dst_idx, dst_label, oldlabel->intList){
			if(src_label == dst_label){
				ok = 1;
				break;
			}
		}
		if(!ok){
			if((rv = __difc_set_label(tsk, oldlabel, src_label, LABEL_OP_ADD, LABEL_TYPE_INT, 1)) < 0){
				difc_debug("Failed to add integrity label %llu\n", src_label);
				return rv;
			}
		}
	}

	/* Then removes */
	list_for_each_label(src_idx, src_label, oldlabel->intList){
		int ok = 0;
		list_for_each_label(dst_idx, dst_label, newlabel->intList){
			if(src_label == dst_label){
				ok = 1;
				break;
			}
		}
		if(!ok){
			if((rv = __difc_set_label(tsk, oldlabel, src_label, LABEL_OP_REMOVE, LABEL_TYPE_INT, 1)) < 0){
				difc_debug("Failed to drop integrity label %llu\n", src_label);
				return rv;
			}
		}
	}

	return 0;
}


/* Just checks whether the task is labeled */
static inline int task_get_labels(const struct task_struct *tsk, struct task_security_struct **security){

	if(tsk->security == NULL)
		return 1;
	
	*security = (struct task_security_struct *) tsk->security;

	/* We can have an empty structure and still be unlabeled */
	if((*(*security)->label.secList) == 0 && (*(*security)->label.intList) == 0){
		return 1;
	}

	return 0;
}

/* Allocation routines */
static int difc_task_alloc_security(struct task_struct *task)
{

	struct task_security_struct *tsec;
	struct task_security_struct *cur_tsec = current->security;
	tsec = kmem_cache_zalloc(difc_task_cachep, GFP_KERNEL);
	if(!tsec) {
	  difc_debug("Out of memory\n");
		return -ENOMEM;
	}

	spin_lock_init(&tsec->cap_lock);
	INIT_LIST_HEAD(&tsec->capList);
	INIT_LIST_HEAD(&tsec->capSuspendList);
	
	/* The capabilities were already checked in security_task_create */
	if(cur_tsec){
		struct cap_segment *n, *m;
		spin_lock(&cur_tsec->cap_lock);
		list_for_each_entry(n, &cur_tsec->capList, list){
			m = alloc_cap_segment();
			if(!m){
				spin_unlock(&cur_tsec->cap_lock);
				difc_debug("Out of memory\n");
				return -ENOMEM;
			}
			INIT_LIST_HEAD(&m->list);
			memcpy(m->caps, n->caps, sizeof(n->caps));
			list_add_tail(&m->list, &tsec->capList);
		}
		spin_unlock(&cur_tsec->cap_lock);
		tsec->tcb = cur_tsec->tcb;
	}

	task->security = tsec;
	return 0;

}

static void difc_task_free_security(struct task_struct *task)
{
	struct task_security_struct *tsec = task->security;
	task->security = NULL;
	if(tsec){
		struct cap_segment *n, *m;
		list_for_each_entry_safe(m, n, &tsec->capList, list)
			free_cap_segment(m);
		list_for_each_entry_safe(m, n, &tsec->capSuspendList, list)
			free_cap_segment(m);
		
		kmem_cache_free(difc_task_cachep, tsec);
	}
}

static int difc_inode_alloc_security(struct inode *inode)
{

	struct object_security_struct *tsec;
	tsec = kmem_cache_zalloc(difc_object_cachep, GFP_KERNEL);
	if(!tsec) {
	  difc_debug("Out of memory\n");
		return -ENOMEM;
	}

	init_rwsem(&tsec->label_change_sem);

	inode->i_security = tsec;
	return 0;

}

static void difc_inode_free_security(struct inode *inode)
{
	struct object_security_struct *tsec = inode->i_security;
	inode->i_security = NULL;
	if(tsec)
		kmem_cache_free(difc_object_cachep, tsec);
}


static int difc_bprm_alloc_security(struct linux_binprm *bprm)
{

	struct object_security_struct *tsec;
	struct object_security_struct *isec = bprm->file->f_dentry->d_inode->i_security;
	tsec = kmem_cache_zalloc(difc_object_cachep, GFP_KERNEL);
	if(!tsec) {
	  difc_debug("Out of memory\n");
		return -ENOMEM;
	}

	// Copy the initial from the file
	if(isec){
		down_read(&isec->label_change_sem);
		memcpy(&tsec->label, &isec->label, sizeof(struct label_struct));
		up_read(&isec->label_change_sem);
	}

	bprm->security = tsec;
	return 0;

}

static void difc_bprm_free_security(struct linux_binprm *bprm)
{
	struct object_security_struct *tsec = bprm->security;
	bprm->security = NULL;
	if(tsec)
		kmem_cache_free(difc_object_cachep, tsec);
}

/* If the brpm is labeled as part of the TCB, set the TCB bit in the task */
static void difc_bprm_apply_creds (struct linux_binprm * bprm, int unsafe){
	struct object_security_struct *tsec = bprm->security;
	struct task_security_struct *ttsec = current->security;
	label_t idx, label;
	int ok = 0;
	list_for_each_label(idx, label, tsec->label.intList){
		if(label == LABEL_TCB){
			ok = 1;
			break;
		}
	}
	if(!ok){
		ttsec->tcb = 0;
		return;
	}

	difc_debug("Loading TCB app\n");
	ttsec->tcb = 1;
}


static inline size_t pack_difc_inode_xattr(char *buf, size_t len,
					   struct label_struct *isec)
{ 
	size_t rv = (*isec->secList) + (*isec->intList) + 2;
	size_t offset;
	rv *= sizeof(label_t);
	
	if(rv < len){
	  difc_debug("Bad xattr %d %d\n", rv, len);
		return -ERANGE;
	}

	offset = ((*isec->secList) + 1) * sizeof(label_t);
	memcpy(buf, isec->secList, offset);
	memcpy(buf + offset, isec->intList, rv - offset);
	return rv;
}

static inline size_t unpack_difc_inode_xattr(const char *buf, size_t len,
					     struct label_struct *isec)
{ 
	label_t *lbuf = (label_t *) buf;
	size_t curbound = 0;

	BUG_ON((*lbuf) + 1 + curbound > len);
	memcpy(isec->secList, buf, ((*buf) + 1) * sizeof(label_t));
	curbound = (*lbuf) + 1;
	lbuf += (*lbuf) + 1;

	BUG_ON((*lbuf) + 1 + curbound > len);
	memcpy(isec->intList, buf, ((*buf) + 1) * sizeof(label_t));
	return 0;
}

/* Init inode security.  Atomically label a newly created inode, and
 * write back the xattr (where supported) 
 */
static int difc_inode_init_security (struct inode *inode, struct inode *dir,
				     char **name, void **value, size_t *len, 
				     void *lbl)
{
	struct object_security_struct *isec = inode->i_security;
	struct label_struct *input_label = (struct label_struct *)lbl;
	struct task_security_struct *tsec = current->security;
	char *namep = NULL, *label;
	size_t mylen;

	/* Initialize the inode's security field */
	BUG_ON(!isec);

	/* if the task doesn't have a security field, treat it as
	 * unlabeled and just return (as isec is initalized to
	 * unlabeled as well 
	 */
	if(!tsec){
	  difc_debug("Bad tsec\n");
		return -EOPNOTSUPP;
	}

	/* Current policy is to copy it from the creating task's label 
	 * unless lbl is not null.  In this case, use the label if it
	 * meets this rule:
	 * 
	 * task label <= lbl <= task capabilities
	 *
	 */
	if(input_label){
		int a, b;
		a = check_allowed(&tsec->label, input_label);
		b = check_label_replace(current, &tsec->label, input_label);

		if((0 == a)
		   && (0 == b))
			memcpy(&isec->label, input_label, sizeof(struct label_struct));
		else {
			printk(KERN_ERR "Ignoring requested label on inode %lu: %d, %d\n", inode->i_ino, a, b);
			return -EPERM;
		}
	} else 
		memcpy(&isec->label, &tsec->label, sizeof(struct label_struct));

	/* If we the inode is unlabeled, don't bother labeling the inode on disk */
	mylen = (*isec->label.secList) + (*isec->label.intList);
	if(mylen == 0){
	  difc_debug("mylen too short\n");
		return -EOPNOTSUPP;
	}
	
	/* Now allocate and fill a name and value buffer*/
	if(name){
		namep = kstrdup(XATTR_DIFC_SUFFIX, GFP_KERNEL);
		if(!namep){
		  difc_debug("oom\n");
			return -ENOMEM;
		}
		*name = namep;
	}

	if(value && len){
		mylen += 2;
		mylen *= sizeof(label_t);

		label = kmalloc(mylen, GFP_KERNEL);
		if(!label){
			kfree(namep);
			difc_debug("oom\n");
			return -ENOMEM;
		}
		/* Marshall the label into a single xattr */
		pack_difc_inode_xattr(label, mylen, &isec->label);

		*value = label;
		*len = mylen;
	}
	return 0;
}

/* get_inode_security.  My best understanding is that this is how the
 * fs requests the serialized version of the security xattr for
 * writeback.
 */

static int difc_inode_getsecurity(const struct inode *inode, const char *name, void *buffer, size_t size, int err)
{
	struct object_security_struct *isec = inode->i_security;

	if (strcmp(name, XATTR_DIFC_SUFFIX)){
	  difc_debug("Bad xattr suffix %s\n", name);
		return -EOPNOTSUPP;
	}

	return pack_difc_inode_xattr(buffer, size, &isec->label);
}

/* set_inode_security.  My best understanding is that this is the hook
 * where we get the xattr back from the filesystem in some close
 * proximity to its being read from disk. 
 */

static int difc_inode_setsecurity(struct inode *inode, const char *name,
				  const void *value, size_t size, int flags)
{
	struct object_security_struct *isec = inode->i_security;

	if(!isec){
	  difc_debug("Bad isec\n");
		return -EOPNOTSUPP;
	}

	if (strcmp(name, XATTR_DIFC_SUFFIX)){
	  difc_debug("Bad xattr suffix %s\n", name);
		return -EOPNOTSUPP;
	}

	if (!value || !size){
	  difc_debug("Bad val or size %p %d\n", value, size);
		return -EACCES;
	}

	return unpack_difc_inode_xattr(value, size, &isec->label);
}

/* list_inode_security.  Returns the difc xattr suffix */

static int difc_inode_listsecurity(struct inode *inode, char *buffer, size_t buffer_size)
{
	const int len = sizeof(XATTR_NAME_DIFC);
	if (buffer && len <= buffer_size)
		memcpy(buffer, XATTR_NAME_DIFC, len);
	return len;
}

/** Need get/set/list security for xattr label management */

/* Permission hooks */

static int difc_inode_permission (struct inode *inode, 
				  int mask, struct nameidata *nd)
{
	struct object_security_struct *isec = inode->i_security;
	struct task_security_struct *tsec = NULL;

	int unlabeled_inode, unlabeled_task;
	int rv = 0;

	/* Get the inode label */
	if(!isec 
	   || ((*isec->label.secList) == 0 && (*isec->label.intList) == 0))
		unlabeled_inode = 1;
	else
		unlabeled_inode = 0;

	/* Get the current task label */
	unlabeled_task = task_get_labels(current, &tsec);

	/* Short-circuit for mutually unlabeled entities */
	if (unlabeled_task && unlabeled_inode)
		return 0;

	if(unlabeled_task && !unlabeled_inode){
		difc_debug("Trying to access with mask %d, inode %lu\n", mask, inode->i_ino);
	}

	/* Check if they are ok */
	if((mask & (MAY_READ|MAY_EXEC)) != 0)
		rv |= check_allowed(&isec->label, &tsec->label);

	if((mask & MAY_WRITE) == MAY_WRITE)
		rv |= check_allowed(&tsec->label, &isec->label);
	
	return rv;
}

/* Just pass file checks through to the inode.  Annoyingly, there
 * aren't init hooks to set this up once.  Hence the practial
 * importance of immutable labels.
 */
static int difc_file_permission(struct file *file, int mask){
	int rv;
	struct object_security_struct *oss = file->f_dentry->d_inode->i_security;
	if(!oss)
		return 0;
	down_read(&oss->label_change_sem);

	rv = difc_inode_permission(file->f_dentry->d_inode, mask, NULL);
	if(rv != 0)
		up_read(&oss->label_change_sem);
	return rv;
}

/* Current policy - only allow socket connect with an empty secrecy
 * label 
 */
static int difc_socket_connect(struct socket *sock, 
			       struct sockaddr *address,
			       int addrlen){
	struct task_security_struct *tsec = current->security;
	difc_debug("Socket connect - sec label is %llu long\n", *tsec->label.secList);
	return (0 != *tsec->label.secList);
}

/* Read the normal attributes (metadata/stat) on a file */
static int difc_inode_getattr (struct vfsmount *mnt, struct dentry *dentry){
	return difc_inode_permission(dentry->d_inode, MAY_READ, NULL);
}

static int difc_inode_setattr(struct dentry *dentry, struct iattr *attr){
	return difc_inode_permission(dentry->d_inode, MAY_WRITE, NULL);
}


/* Release the semaphore on this file's label */
static void difc_file_rw_release(struct file *file){
	struct object_security_struct *oss = file->f_dentry->d_inode->i_security;
	if(oss)
		up_read(&oss->label_change_sem);
}

#define REGION_NONE  0
#define REGION_SELF  1
#define REGION_GROUP 2


static inline void __difc_alloc_label(struct task_struct *tsk, capability_t new_cap){
	int ok = 0;
	struct cap_segment *n;
	struct task_security_struct *tsec;

	tsec = tsk->security;

	/* Add the capability to the task's list.  Just use some BUG's
	 * for now.
	 */
	BUG_ON(!tsec);

	spin_lock(&tsec->cap_lock);
		
	list_for_each_entry(n, &tsec->capList, list){
		if(n->caps[0] < CAP_LIST_MAX_ENTRIES){
			ok = 1;
			break;
		}
	}
	if(!ok){
		n = alloc_cap_segment();
		INIT_LIST_HEAD(&n->list);
		list_add_tail(&n->list, &tsec->capList);
	}
		
	n->caps[++(n->caps[0])] = new_cap;
	spin_unlock(&tsec->cap_lock);
}

/* Allocate a new capability and put it in the task's capability set */
static label_t difc_alloc_label(struct task_struct *tsk, int type, int region){

	capability_t new_cap = atomic_inc_return(&cur_max_capability);


	/* Only allocate the requested type */
	new_cap |= (type & (CAP_PLUS_MASK| CAP_MINUS_MASK));


	if(region == REGION_NONE){
		/* Pointless */
	} else if(region == REGION_SELF){
		__difc_alloc_label(tsk, new_cap);
	} else if(region == REGION_GROUP){
		struct task_struct *t = tsk;
		difc_debug("Adding label cap to self\n");
		__difc_alloc_label(tsk, new_cap);
		while_each_thread(tsk, t){
		  difc_debug("Adding label cap to task %d\n", t->pid);
		  __difc_alloc_label(t, new_cap);
		}
	}

	return (new_cap & CAP_LABEL_MASK);
}



static void *difc_copy_user_label(const char __user *label)
{
	int rv;
	void *buf;
	buf = kmalloc(sizeof(struct label_struct), GFP_KERNEL);
	if(!buf)
		return NULL;
	rv = copy_from_user(buf, label, sizeof(struct label_struct));
	if(rv){
		printk(KERN_ERR "Bad copy: %d bytes missing\n", rv);
		kfree(buf);
		return NULL;
	}
	return buf;
}

static int difc_set_task_label(struct task_struct *tsk, label_t label, int op_type, int label_type, void __user *bulk_label){
	int rv;
	struct label_struct *ulabel;
	struct task_security_struct *tsec = tsk->security;

	if(op_type == LABEL_OP_REPLACE){
		difc_debug("set_task_label: doing a replace: %d\n", op_type);
		ulabel = difc_copy_user_label(bulk_label);
		if(!ulabel){
		  difc_debug("Bad ulabel\n");
		  return -ENOMEM;
		}

		if((rv = check_label_replace(tsk, &tsec->label, ulabel)) == 0){
			memcpy(&tsec->label, ulabel, sizeof(struct label_struct));
		} 
		kfree(ulabel);
		return rv;
	} 

	difc_debug("set_task_label: not a replace: %d\n", op_type);

	return __difc_set_label(tsk, &tsec->label, label, op_type, label_type, 0);
}

/* Set the label on an existing inode 
 *
 * XXX: Should do something about mmapped files.
 */
static int difc_inode_set_label(struct inode *inode, void __user *new_label){
	
	struct object_security_struct *oss = inode->i_security;
	struct label_struct *ulabel = difc_copy_user_label(new_label);
	int rv;
	if(!ulabel){
	  difc_debug("out o' mem\n");
		return -ENOMEM;
	}

	down_write(&oss->label_change_sem);
	
	/* Change that the label change is legal for the current task
	 */
	if((rv = check_label_replace(current, &oss->label, ulabel)) == 0){
		/* Iterate over the parent list and check those labels too */
		struct dentry *dentry;
		/* I think I really need the dcache lock for this to
		 * be safe.  Code calling this should not be holding
		 * it, AFAIK.
		 */
		spin_lock(&dcache_lock);
		list_for_each_entry(dentry, &inode->i_dentry, d_alias){
			struct dentry *parent = dentry->d_parent;
			struct inode *p_inode = parent->d_inode;
			
			/* Specifically, check that we can write data into the parent */
			rv |= difc_inode_permission(p_inode, MAY_WRITE, NULL);

			if(rv)
				break;
		}
		spin_unlock(&dcache_lock);
	} 

	if(rv == 0)
		memcpy(&oss->label, ulabel, sizeof(struct label_struct));

	up_write(&oss->label_change_sem);
	return rv;
}

#define CAP_OP_FLAG_PERMANENT 0
#define CAP_OP_FLAG_TEMPORARY 1

static int difc_drop_capabilities(void __user *buf, unsigned int len, int type, int flag){
	int rv, i;
	struct task_security_struct *tsec = current->security;
	capability_t *capList = kmalloc(sizeof(capability_t) * len, GFP_KERNEL);
	capability_t tmp, idx, l;
	if(!capList){
	  	  difc_debug("no mo mem\n");
		return -ENOMEM;
	}

	rv = copy_from_user(capList, buf, sizeof(capability_t) * len);
	if(rv){
		printk(KERN_ERR "difc_drop_capabilities: Bad copy: %d bytes missing\n", rv);
		kfree(capList);
		return -ENOMEM;
	}
	
	spin_lock(&tsec->cap_lock);
	for(i = 0; i < len; i++){
		int ok = 0;
		struct cap_segment *n;
		tmp = capList[i];
		
		list_for_each_cap(idx, l, n, tsec->capList){
			if(tmp == (l & CAP_LABEL_MASK)){
 				difc_debug("Found Cap: %llu\n", l);
				ok = 1;
				/* Do the drop*/
				if((type & CAP_PLUS_MASK)){
					if((l & CAP_PLUS_MASK)){
						l ^= CAP_PLUS_MASK;
						tmp |= CAP_PLUS_MASK;
						if((l & CAP_MINUS_MASK) == 0){
							while(idx < (n->caps[0])){
								n->caps[idx] = n->caps[idx+1];
								idx++;
							}
							(n->caps[0])--;
							break;
						}
					} else  /* Don't suspend a cap that isn't there */
						rv = -ENOENT;
				} 

				if((type & CAP_MINUS_MASK)){
					if((l & CAP_MINUS_MASK)){
						l ^= CAP_MINUS_MASK;
						tmp |= CAP_MINUS_MASK;
						if((l & CAP_PLUS_MASK) == 0){
							while(idx < (n->caps[0])){
								n->caps[idx] = n->caps[idx+1];
								idx++;
							}
							(n->caps[0])--;
							break;
						}
					} else  /* Don't suspend a cap that isn't there */
						rv = -ENOENT;
				} 
				
				difc_debug("Cap now equals %llu\n", l);
				break;
			}
		}
		if(!ok){
			difc_debug("Missing capability: %llu\n", tmp);
			rv = -ENOENT;
		}
		if(flag == CAP_OP_FLAG_TEMPORARY){
			struct cap_segment *m;
			int ok1 = 0;
			list_for_each_entry(m, &tsec->capSuspendList, list){
				if(m->caps[0] < CAP_LIST_MAX_ENTRIES){
					ok1 = 1;
					break;
				}
			}
			if(!ok1){
				m = alloc_cap_segment();
				INIT_LIST_HEAD(&m->list);
				list_add_tail(&m->list, &tsec->capSuspendList);
			}

			m->caps[++(m->caps[0])] = tmp;
		}
	}
	spin_unlock(&tsec->cap_lock);

	kfree(capList);
	return rv;
}

/* Restore a capability from the suspend list */
static int difc_resume_capabilities(void __user *buf, unsigned int len, int type){
	int rv, i;
	struct task_security_struct *tsec = current->security;
	capability_t *capList = kmalloc(sizeof(capability_t) * len, GFP_KERNEL);
	capability_t tmp, idx, l;
	if(!capList){
	  	  difc_debug("no mo mem\n");
		return -ENOMEM;
	}

	rv = copy_from_user(capList, buf, sizeof(capability_t) * len);
	if(rv){
		printk(KERN_ERR "difc_resume_capabilities: Bad copy: %d bytes missing\n", rv);
		kfree(capList);
		return -ENOMEM;
	}
	
	spin_lock(&tsec->cap_lock);
	for(i = 0; i < len; i++){
		int ok = 0;
		struct cap_segment *m;
		int ok1 = 0;
		struct cap_segment *n;
		tmp = capList[i];

		list_for_each_cap(idx, l, n, tsec->capSuspendList){
			if(tmp == (l & CAP_LABEL_MASK)){
 				difc_debug("Found Cap: %llu\n", l);
				ok = 1;
				/* Do the drop*/
				if((type & CAP_PLUS_MASK)){
					if((l & CAP_PLUS_MASK)){
						l ^= CAP_PLUS_MASK;
						tmp |= CAP_PLUS_MASK;
						if((l & CAP_MINUS_MASK) == 0){
							while(idx < (n->caps[0])){
								n->caps[idx] = n->caps[idx+1];
								idx++;
							}
							(n->caps[0])--;
							break;
						}
					} else  /* Don't resume a cap that isn't there */
						rv = -ENOENT;
				} 

				if((type & CAP_MINUS_MASK)){
					if((l & CAP_MINUS_MASK)){
						l ^= CAP_MINUS_MASK;
						tmp |= CAP_MINUS_MASK;
						if((l & CAP_PLUS_MASK) == 0){
							while(idx < (n->caps[0])){
								n->caps[idx] = n->caps[idx+1];
								idx++;
							}
							(n->caps[0])--;
							break;
						}
					} else  /* Don't resume a cap that isn't there */
						rv = -ENOENT;
				} 
				
				difc_debug("Cap now equals %llu\n", l);
				break;
			}
		}
		if(!ok){
			difc_debug("Missing capability: %llu\n", tmp);
			rv = -ENOENT;
		}

		list_for_each_entry(m, &tsec->capList, list){
			if(m->caps[0] < CAP_LIST_MAX_ENTRIES){
				ok1 = 1;
				break;
			}
		}
		if(!ok1){
			m = alloc_cap_segment();
			INIT_LIST_HEAD(&m->list);
			list_add_tail(&m->list, &tsec->capList);
		}

		m->caps[++(m->caps[0])] = tmp;
	}
	spin_unlock(&tsec->cap_lock);

	kfree(capList);
	return rv;
}


/* Send a capability to another process */
static int difc_send_capabilities(pid_t pid, void __user *buf, unsigned int len, int type){
	int rv, i;
	struct task_security_struct *tsec = current->security;
	struct task_struct *dest = find_task_by_pid(pid);
	struct task_security_struct *rsec;
	capability_t *capList;
	capability_t tmp, idx, l;

	if(!dest)
		return -ESRCH;

	rsec = dest->security;

	/* Verify that the sender can write to the receiver */
	if(check_allowed(&tsec->label, &rsec->label))
		return -EPERM;

	capList = kmalloc(sizeof(capability_t) * len, GFP_KERNEL);
	if(!capList){
	  	  difc_debug("no mo mem\n");
		return -ENOMEM;
	}

	rv = copy_from_user(capList, buf, sizeof(capability_t) * len);
	if(rv){
		printk(KERN_ERR "difc_drop_capabilities: Bad copy: %d bytes missing\n", rv);
		kfree(capList);
		return -ENOMEM;
	}

	/* Lock the caps for both sender and receiver, in ascending virtual addr */
	if(&tsec->cap_lock < &rsec->cap_lock){
		spin_lock(&tsec->cap_lock);
		spin_lock(&rsec->cap_lock);
	} else {
		spin_lock(&rsec->cap_lock);
		spin_lock(&tsec->cap_lock);
	}

	for(i = 0; i < len; i++){
		int ok = 0;
		struct cap_segment *n;
		struct cap_segment *m;
		int ok1 = 0;
		tmp = capList[i];
		
		list_for_each_cap(idx, l, n, tsec->capList){
			if(tmp == (l & CAP_LABEL_MASK)){
 				difc_debug("Found Cap: %llu\n", l);
				ok = 1;
				/* Do the drop*/
				if((type & CAP_PLUS_MASK)){
					if((l & CAP_PLUS_MASK)){
						l ^= CAP_PLUS_MASK;
						tmp |= CAP_PLUS_MASK;
						if((l & CAP_MINUS_MASK) == 0){
							while(idx < (n->caps[0])){
								n->caps[idx] = n->caps[idx+1];
								idx++;
							}
							(n->caps[0])--;
							break;
						}
					} else  /* Don't suspend a cap that isn't there */
						rv = -ENOENT;
				} 

				if((type & CAP_MINUS_MASK)){
					if((l & CAP_MINUS_MASK)){
						l ^= CAP_MINUS_MASK;
						tmp |= CAP_MINUS_MASK;
						if((l & CAP_PLUS_MASK) == 0){
							while(idx < (n->caps[0])){
								n->caps[idx] = n->caps[idx+1];
								idx++;
							}
							(n->caps[0])--;
							break;
						}
					} else  /* Don't suspend a cap that isn't there */
						rv = -ENOENT;
				} 
				
				difc_debug("Cap now equals %llu\n", l);
				break;
			}
		}
		if(!ok){
			difc_debug("Missing capability: %llu\n", tmp);
			rv = -ENOENT;
		}

		list_for_each_entry(m, &rsec->capSuspendList, list){
			if(m->caps[0] < CAP_LIST_MAX_ENTRIES){
				ok1 = 1;
				break;
			}
		}
		if(!ok1){
			m = alloc_cap_segment();
			INIT_LIST_HEAD(&m->list);
			list_add_tail(&m->list, &rsec->capSuspendList);
		}
		
		m->caps[++(m->caps[0])] = tmp;
	}

	/* Lock the caps for both sender and receiver, in ascending virtual addr */
	if(&tsec->cap_lock < &rsec->cap_lock){
		spin_unlock(&rsec->cap_lock);
		spin_unlock(&tsec->cap_lock);
	} else {
		spin_unlock(&tsec->cap_lock);
		spin_unlock(&rsec->cap_lock);
	}

	kfree(capList);
	return rv;
}


/* This is unrestricted label setting.  Only for tasks with the special
 * Trusted Computing Base (TCB) int label */
static int difc_replace_label_tcb(void __user *label){
	struct task_security_struct *tsec = current->security;
	struct label_struct *ulabel;

	/* Check for TCB Label */
	if(!tsec->tcb){
		difc_debug("not tcb\n");
		return -EPERM;
	}

	ulabel = difc_copy_user_label(label);
	if(!ulabel){
	  difc_debug("no memry\n");
		return -ENOMEM;
	}

	memcpy(&tsec->label, ulabel, sizeof(struct label_struct));
	kfree(ulabel);

	return 0;
}

/* Current policy for pipe full - suppress unless both the inode and
 * the writer are unlabeled 
 */
static int difc_pipe_full(struct inode *inode){
	struct object_security_struct *isec = inode->i_security;
	struct task_security_struct *tsec = current->security;
	if(isec->label.secList == 0
	   && isec->label.intList == 0
	   && tsec->label.secList == 0
	   && tsec->label.intList == 0)
		return 0;
	return 1;
}

static struct security_operations difc_security_ops = {
	/* brpm ops */
	.bprm_alloc_security =          difc_bprm_alloc_security,
	.bprm_free_security =           difc_bprm_free_security,
	.bprm_apply_creds =             difc_bprm_apply_creds,

        /* Start with file open, and co */
	.inode_alloc_security =         difc_inode_alloc_security,
	.inode_free_security =          difc_inode_free_security,
	.inode_init_security =          difc_inode_init_security,
	.inode_permission =		difc_inode_permission,
	.inode_getsecurity =            difc_inode_getsecurity,
	.inode_setsecurity =            difc_inode_setsecurity,
	.inode_listsecurity =           difc_inode_listsecurity,
	.inode_setattr =                difc_inode_setattr,
	.inode_getattr =                difc_inode_getattr,
	/* File ops */
	.file_permission =              difc_file_permission,

	/* Socket ops */
	.socket_connect =                difc_socket_connect,

	/* Need task security allocation */
	.task_alloc_security =          difc_task_alloc_security,
	.task_free_security =           difc_task_free_security,
	/* Custom hooks */
	.alloc_label =                  difc_alloc_label,
	.set_task_label =               difc_set_task_label,
	.copy_user_label =              difc_copy_user_label,
	.drop_capabilities =            difc_drop_capabilities,
	.resume_capabilities =          difc_resume_capabilities,
	.send_capabilities =           difc_send_capabilities,
	.file_rw_release =              difc_file_rw_release,
	.inode_set_label =              difc_inode_set_label,
	.replace_label_tcb =            difc_replace_label_tcb,
	.pipe_full =                    difc_pipe_full,
};

static int __init difc_init (void)
{
	/* Allocate the task security data cache */
	difc_task_cachep = 
		kmem_cache_create("difc_task_struct",
				  sizeof(struct task_security_struct),
				  0, SLAB_PANIC, NULL, NULL);

	difc_object_cachep = 
		kmem_cache_create("difc_object_struct",
				  sizeof(struct object_security_struct),
				  0, SLAB_PANIC, NULL, NULL);

	difc_caps_cachep = 
		kmem_cache_create("difc_cap_segment",
				  sizeof(struct cap_segment),
				  0, SLAB_PANIC, NULL, NULL);

	/* Initialize the capability allocation counter
	 *
	 * XXX: Figure out how to persistently store these
	 */
	atomic_set(&cur_max_capability, CAP_START_VAL);

	/* register ourselves with the security framework */
	if (register_security (&difc_security_ops)) {
		printk (KERN_INFO 
			"Failure registering DIFC module with the kernel\n");
		/* try registering with primary module */
		if (mod_reg_security (MY_NAME, &difc_security_ops)) {
			printk (KERN_INFO "Failure registering DIFC "
				" module with primary security module.\n");
			return -EINVAL;
		}
		secondary = 1;
	}
	/* Set up the proc file */
	difc_debug_procfile = create_proc_entry("difc_debug", 0600, 0);
	difc_debug_procfile->read_proc = difc_debug_procfile_read;
	difc_debug_procfile->write_proc = difc_debug_procfile_write;

	printk (KERN_INFO "DIFC module initialized\n");

	return 0;
}

static void __exit difc_exit (void)
{
	/* remove ourselves from the security framework */
	if (secondary) {
		if (mod_unreg_security (MY_NAME, &difc_security_ops))
			printk (KERN_INFO "Failure unregistering DIFC "
				" module with primary module.\n");
	} else { 
		if (unregister_security (&difc_security_ops)) {
			printk (KERN_INFO "Failure unregistering DIFC "
				"module with the kernel\n");
		}
	}
	printk (KERN_INFO "DIFC module removed\n");
}

security_initcall (difc_init);
module_exit (difc_exit);

MODULE_DESCRIPTION("DIFC LSM module, written to curry the favor of Indrajit Roy");
MODULE_LICENSE("GPL");

