// Standard headers for difc system calls

#include <inttypes.h>
#include <stdlib.h>
#include <stdio.h>

typedef uint64_t label_t;
typedef uint64_t capability_t;


#define LABEL_LIST_BYTES 256
#define LABEL_LIST_LABELS (LABEL_LIST_BYTES / sizeof(label_t))
#define LABEL_LIST_MAX_ENTRIES (LABEL_LIST_BYTES / sizeof(label_t)) - 1

struct label_struct {
	label_t secList[LABEL_LIST_LABELS];
	label_t intList[LABEL_LIST_LABELS];
};

#define __NR_alloc_label         328
#define __NR_set_task_label      329
#define __NR_mkdir_labeled       330
#define __NR_drop_capabilities   331
#define __NR_create_labeled      332
#define __NR_set_file_label      333
#define __NR_replace_label_tcb   334

#define REGION_NONE  0
#define REGION_SELF  1
#define REGION_GROUP 2



#define LABEL_OP_ADD     0
#define LABEL_OP_REMOVE  1
#define LABEL_OP_REPLACE 2

#define LABEL_TYPE_SEC  0
#define LABEL_TYPE_INT  1

#define CAP_PLUS_MASK  (1<<30)
#define CAP_MINUS_MASK (1<<31)

#include <errno.h>

// type - CAP_PLUS_MASK, CAP_MINUS_MASK, or them or'ed together  

static inline label_t alloc_label(int type, int region){
  int rv = syscall(__NR_alloc_label, type, region);
  return rv ? rv : -errno;
}

// ulabel can be a label struct, when used with LABEL_OP_REPLACE
static inline int set_task_label(label_t label, int op, int label_type, 
				 struct label_struct *ulabel){
  int rv = syscall(__NR_set_task_label, label, op, label_type, ulabel);
  return rv ? -errno : 0;
}

// Iterators borrowed from kernel
#define list_for_each_label(idx, l, head)	\
	for(idx = 1; idx <= *(head) && ({l = head[idx]; 1; }); idx++)

static inline int mkdir_labeled (const char* pathname, mode_t mode, struct label_struct *label){
  int rv = syscall(__NR_mkdir_labeled, pathname, mode, label);
  return rv ? -errno : 0;
}

static inline int create_labeled (const char* pathname, mode_t mode, struct label_struct *label){
  int rv = syscall(__NR_create_labeled, pathname, mode, label);
  return rv ? -errno : 0;
}

#define CAP_OP_FLAG_PERMANENT 0
#define CAP_OP_FLAG_TEMPORARY 1


/* drop_capablities
 *
 * list - an array of capability_t's
 * len - the length of the list in caps, not bytes
 * type - CAP_PLUS_MASK, CAP_MINUS_MASK, or them or'ed together  
 * flag - permanent or temporary
 *
 * rv: -1 on error
 *  errno: ENOMEM (allocation for copy failed)
 *         ENOENT (attempt to drop a cap not in the cap set)
 */
static inline int drop_capabilities(capability_t *list, unsigned int len, int type, int flag){
  int rv = syscall(__NR_drop_capabilities, list, len, type, flag);
  return rv ? -errno : 0;
}

static inline int set_file_label(const char *path, struct label_struct *ulabel){
  int rv = syscall(__NR_set_file_label, path, ulabel);
  return rv ? -errno : 0;
}

static inline int replace_label_tcb(struct label_struct *label){
  int rv = syscall(__NR_replace_label_tcb, label);
  return rv ? -errno : 0;
}
