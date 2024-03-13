//package com.jd.mpc.domain.task;
//
//import java.time.LocalDateTime;
//import java.util.ArrayList;
//import java.util.List;
//
//public class ChildrenTaskExample {
//    /**
//     * orderByClause
//     */
//    protected String orderByClause;
//
//    /**
//     * distinct
//     */
//    protected boolean distinct;
//
//    /**
//     * oredCriteria
//     */
//    protected List<Criteria> oredCriteria;
//
//    /**
//     * offset
//     */
//    protected Integer offset;
//
//    /**
//     * rows
//     */
//    protected Integer rows;
//
//    /**
//    children_task
//     */
//    public ChildrenTaskExample() {
//        oredCriteria = new ArrayList<Criteria>();
//    }
//
//    /**
//    children_task
//     */
//    public void setOrderByClause(String orderByClause) {
//        this.orderByClause = orderByClause;
//    }
//
//    /**
//    children_task
//     */
//    public String getOrderByClause() {
//        return orderByClause;
//    }
//
//    /**
//    children_task
//     */
//    public void setDistinct(boolean distinct) {
//        this.distinct = distinct;
//    }
//
//    /**
//    children_task
//     */
//    public boolean isDistinct() {
//        return distinct;
//    }
//
//    /**
//    children_task
//     */
//    public List<Criteria> getOredCriteria() {
//        return oredCriteria;
//    }
//
//    /**
//    children_task
//     */
//    public void or(Criteria criteria) {
//        oredCriteria.add(criteria);
//    }
//
//    /**
//    children_task
//     */
//    public Criteria or() {
//        Criteria criteria = createCriteriaInternal();
//        oredCriteria.add(criteria);
//        return criteria;
//    }
//
//    /**
//    children_task
//     */
//    public Criteria createCriteria() {
//        Criteria criteria = createCriteriaInternal();
//        if (oredCriteria.size() == 0) {
//            oredCriteria.add(criteria);
//        }
//        return criteria;
//    }
//
//    /**
//    children_task
//     */
//    protected Criteria createCriteriaInternal() {
//        Criteria criteria = new Criteria();
//        return criteria;
//    }
//
//    /**
//    children_task
//     */
//    public void clear() {
//        oredCriteria.clear();
//        orderByClause = null;
//        distinct = false;
//        rows = null;
//        offset = null;
//    }
//
//    /**
//    children_task
//     */
//    public void setOffset(Integer offset) {
//        this.offset = offset;
//    }
//
//    /**
//    children_task
//     */
//    public Integer getOffset() {
//        return this.offset;
//    }
//
//    /**
//    children_task
//     */
//    public void setRows(Integer rows) {
//        this.rows = rows;
//    }
//
//    /**
//    children_task
//     */
//    public Integer getRows() {
//        return this.rows;
//    }
//
//    /**
//    children_task
//     */
//    public ChildrenTaskExample limit(Integer rows) {
//        this.rows = rows;
//        return this;
//    }
//
//    /**
//    children_task
//     */
//    public ChildrenTaskExample limit(Integer offset, Integer rows) {
//        this.offset = offset;
//        this.rows = rows;
//        return this;
//    }
//
//    /**
//    children_task
//     */
//    public ChildrenTaskExample page(Integer page, Integer pageSize) {
//        this.offset = page * pageSize;
//        this.rows = pageSize;
//        return this;
//    }
//
//    protected abstract static class GeneratedCriteria {
//        protected List<Criterion> criteria;
//
//        protected GeneratedCriteria() {
//            super();
//            criteria = new ArrayList<Criterion>();
//        }
//
//        public boolean isValid() {
//            return criteria.size() > 0;
//        }
//
//        public List<Criterion> getAllCriteria() {
//            return criteria;
//        }
//
//        public List<Criterion> getCriteria() {
//            return criteria;
//        }
//
//        protected void addCriterion(String condition) {
//            if (condition == null) {
//                throw new RuntimeException("Value for condition cannot be null");
//            }
//            criteria.add(new Criterion(condition));
//        }
//
//        protected void addCriterion(String condition, Object value, String property) {
//            if (value == null) {
//                throw new RuntimeException("Value for " + property + " cannot be null");
//            }
//            criteria.add(new Criterion(condition, value));
//        }
//
//        protected void addCriterion(String condition, Object value1, Object value2, String property) {
//            if (value1 == null || value2 == null) {
//                throw new RuntimeException("Between values for " + property + " cannot be null");
//            }
//            criteria.add(new Criterion(condition, value1, value2));
//        }
//
//        public Criteria andIdIsNull() {
//            addCriterion("id is null");
//            return (Criteria) this;
//        }
//
//        public Criteria andIdIsNotNull() {
//            addCriterion("id is not null");
//            return (Criteria) this;
//        }
//
//        public Criteria andIdEqualTo(Long value) {
//            addCriterion("id =", value, "id");
//            return (Criteria) this;
//        }
//
//        public Criteria andIdNotEqualTo(Long value) {
//            addCriterion("id <>", value, "id");
//            return (Criteria) this;
//        }
//
//        public Criteria andIdGreaterThan(Long value) {
//            addCriterion("id >", value, "id");
//            return (Criteria) this;
//        }
//
//        public Criteria andIdGreaterThanOrEqualTo(Long value) {
//            addCriterion("id >=", value, "id");
//            return (Criteria) this;
//        }
//
//        public Criteria andIdLessThan(Long value) {
//            addCriterion("id <", value, "id");
//            return (Criteria) this;
//        }
//
//        public Criteria andIdLessThanOrEqualTo(Long value) {
//            addCriterion("id <=", value, "id");
//            return (Criteria) this;
//        }
//
//        public Criteria andIdIn(List<Long> values) {
//            addCriterion("id in", values, "id");
//            return (Criteria) this;
//        }
//
//        public Criteria andIdNotIn(List<Long> values) {
//            addCriterion("id not in", values, "id");
//            return (Criteria) this;
//        }
//
//        public Criteria andIdBetween(Long value1, Long value2) {
//            addCriterion("id between", value1, value2, "id");
//            return (Criteria) this;
//        }
//
//        public Criteria andIdNotBetween(Long value1, Long value2) {
//            addCriterion("id not between", value1, value2, "id");
//            return (Criteria) this;
//        }
//
//        public Criteria andParentTaskIdIsNull() {
//            addCriterion("parent_task_id is null");
//            return (Criteria) this;
//        }
//
//        public Criteria andParentTaskIdIsNotNull() {
//            addCriterion("parent_task_id is not null");
//            return (Criteria) this;
//        }
//
//        public Criteria andParentTaskIdEqualTo(String value) {
//            addCriterion("parent_task_id =", value, "parentTaskId");
//            return (Criteria) this;
//        }
//
//        public Criteria andParentTaskIdNotEqualTo(String value) {
//            addCriterion("parent_task_id <>", value, "parentTaskId");
//            return (Criteria) this;
//        }
//
//        public Criteria andParentTaskIdGreaterThan(String value) {
//            addCriterion("parent_task_id >", value, "parentTaskId");
//            return (Criteria) this;
//        }
//
//        public Criteria andParentTaskIdGreaterThanOrEqualTo(String value) {
//            addCriterion("parent_task_id >=", value, "parentTaskId");
//            return (Criteria) this;
//        }
//
//        public Criteria andParentTaskIdLessThan(String value) {
//            addCriterion("parent_task_id <", value, "parentTaskId");
//            return (Criteria) this;
//        }
//
//        public Criteria andParentTaskIdLessThanOrEqualTo(String value) {
//            addCriterion("parent_task_id <=", value, "parentTaskId");
//            return (Criteria) this;
//        }
//
//        public Criteria andParentTaskIdLike(String value) {
//            addCriterion("parent_task_id like", value, "parentTaskId");
//            return (Criteria) this;
//        }
//
//        public Criteria andParentTaskIdNotLike(String value) {
//            addCriterion("parent_task_id not like", value, "parentTaskId");
//            return (Criteria) this;
//        }
//
//        public Criteria andParentTaskIdIn(List<String> values) {
//            addCriterion("parent_task_id in", values, "parentTaskId");
//            return (Criteria) this;
//        }
//
//        public Criteria andParentTaskIdNotIn(List<String> values) {
//            addCriterion("parent_task_id not in", values, "parentTaskId");
//            return (Criteria) this;
//        }
//
//        public Criteria andParentTaskIdBetween(String value1, String value2) {
//            addCriterion("parent_task_id between", value1, value2, "parentTaskId");
//            return (Criteria) this;
//        }
//
//        public Criteria andParentTaskIdNotBetween(String value1, String value2) {
//            addCriterion("parent_task_id not between", value1, value2, "parentTaskId");
//            return (Criteria) this;
//        }
//
//        public Criteria andSubIdIsNull() {
//            addCriterion("sub_id is null");
//            return (Criteria) this;
//        }
//
//        public Criteria andSubIdIsNotNull() {
//            addCriterion("sub_id is not null");
//            return (Criteria) this;
//        }
//
//        public Criteria andSubIdEqualTo(Integer value) {
//            addCriterion("sub_id =", value, "subId");
//            return (Criteria) this;
//        }
//
//        public Criteria andSubIdNotEqualTo(Integer value) {
//            addCriterion("sub_id <>", value, "subId");
//            return (Criteria) this;
//        }
//
//        public Criteria andSubIdGreaterThan(Integer value) {
//            addCriterion("sub_id >", value, "subId");
//            return (Criteria) this;
//        }
//
//        public Criteria andSubIdGreaterThanOrEqualTo(Integer value) {
//            addCriterion("sub_id >=", value, "subId");
//            return (Criteria) this;
//        }
//
//        public Criteria andSubIdLessThan(Integer value) {
//            addCriterion("sub_id <", value, "subId");
//            return (Criteria) this;
//        }
//
//        public Criteria andSubIdLessThanOrEqualTo(Integer value) {
//            addCriterion("sub_id <=", value, "subId");
//            return (Criteria) this;
//        }
//
//        public Criteria andSubIdIn(List<Integer> values) {
//            addCriterion("sub_id in", values, "subId");
//            return (Criteria) this;
//        }
//
//        public Criteria andSubIdNotIn(List<Integer> values) {
//            addCriterion("sub_id not in", values, "subId");
//            return (Criteria) this;
//        }
//
//        public Criteria andSubIdBetween(Integer value1, Integer value2) {
//            addCriterion("sub_id between", value1, value2, "subId");
//            return (Criteria) this;
//        }
//
//        public Criteria andSubIdNotBetween(Integer value1, Integer value2) {
//            addCriterion("sub_id not between", value1, value2, "subId");
//            return (Criteria) this;
//        }
//
//        public Criteria andTaskIndexIsNull() {
//            addCriterion("task_index is null");
//            return (Criteria) this;
//        }
//
//        public Criteria andTaskIndexIsNotNull() {
//            addCriterion("task_index is not null");
//            return (Criteria) this;
//        }
//
//        public Criteria andTaskIndexEqualTo(Integer value) {
//            addCriterion("task_index =", value, "taskIndex");
//            return (Criteria) this;
//        }
//
//        public Criteria andTaskIndexNotEqualTo(Integer value) {
//            addCriterion("task_index <>", value, "taskIndex");
//            return (Criteria) this;
//        }
//
//        public Criteria andTaskIndexGreaterThan(Integer value) {
//            addCriterion("task_index >", value, "taskIndex");
//            return (Criteria) this;
//        }
//
//        public Criteria andTaskIndexGreaterThanOrEqualTo(Integer value) {
//            addCriterion("task_index >=", value, "taskIndex");
//            return (Criteria) this;
//        }
//
//        public Criteria andTaskIndexLessThan(Integer value) {
//            addCriterion("task_index <", value, "taskIndex");
//            return (Criteria) this;
//        }
//
//        public Criteria andTaskIndexLessThanOrEqualTo(Integer value) {
//            addCriterion("task_index <=", value, "taskIndex");
//            return (Criteria) this;
//        }
//
//        public Criteria andTaskIndexIn(List<Integer> values) {
//            addCriterion("task_index in", values, "taskIndex");
//            return (Criteria) this;
//        }
//
//        public Criteria andTaskIndexNotIn(List<Integer> values) {
//            addCriterion("task_index not in", values, "taskIndex");
//            return (Criteria) this;
//        }
//
//        public Criteria andTaskIndexBetween(Integer value1, Integer value2) {
//            addCriterion("task_index between", value1, value2, "taskIndex");
//            return (Criteria) this;
//        }
//
//        public Criteria andTaskIndexNotBetween(Integer value1, Integer value2) {
//            addCriterion("task_index not between", value1, value2, "taskIndex");
//            return (Criteria) this;
//        }
//
//        public Criteria andPodNumIsNull() {
//            addCriterion("pod_num is null");
//            return (Criteria) this;
//        }
//
//        public Criteria andPodNumIsNotNull() {
//            addCriterion("pod_num is not null");
//            return (Criteria) this;
//        }
//
//        public Criteria andPodNumEqualTo(Integer value) {
//            addCriterion("pod_num =", value, "podNum");
//            return (Criteria) this;
//        }
//
//        public Criteria andPodNumNotEqualTo(Integer value) {
//            addCriterion("pod_num <>", value, "podNum");
//            return (Criteria) this;
//        }
//
//        public Criteria andPodNumGreaterThan(Integer value) {
//            addCriterion("pod_num >", value, "podNum");
//            return (Criteria) this;
//        }
//
//        public Criteria andPodNumGreaterThanOrEqualTo(Integer value) {
//            addCriterion("pod_num >=", value, "podNum");
//            return (Criteria) this;
//        }
//
//        public Criteria andPodNumLessThan(Integer value) {
//            addCriterion("pod_num <", value, "podNum");
//            return (Criteria) this;
//        }
//
//        public Criteria andPodNumLessThanOrEqualTo(Integer value) {
//            addCriterion("pod_num <=", value, "podNum");
//            return (Criteria) this;
//        }
//
//        public Criteria andPodNumIn(List<Integer> values) {
//            addCriterion("pod_num in", values, "podNum");
//            return (Criteria) this;
//        }
//
//        public Criteria andPodNumNotIn(List<Integer> values) {
//            addCriterion("pod_num not in", values, "podNum");
//            return (Criteria) this;
//        }
//
//        public Criteria andPodNumBetween(Integer value1, Integer value2) {
//            addCriterion("pod_num between", value1, value2, "podNum");
//            return (Criteria) this;
//        }
//
//        public Criteria andPodNumNotBetween(Integer value1, Integer value2) {
//            addCriterion("pod_num not between", value1, value2, "podNum");
//            return (Criteria) this;
//        }
//
//        public Criteria andStatusIsNull() {
//            addCriterion("status is null");
//            return (Criteria) this;
//        }
//
//        public Criteria andStatusIsNotNull() {
//            addCriterion("status is not null");
//            return (Criteria) this;
//        }
//
//        public Criteria andStatusEqualTo(Integer value) {
//            addCriterion("status =", value, "status");
//            return (Criteria) this;
//        }
//
//        public Criteria andStatusNotEqualTo(Integer value) {
//            addCriterion("status <>", value, "status");
//            return (Criteria) this;
//        }
//
//        public Criteria andStatusGreaterThan(Integer value) {
//            addCriterion("status >", value, "status");
//            return (Criteria) this;
//        }
//
//        public Criteria andStatusGreaterThanOrEqualTo(Integer value) {
//            addCriterion("status >=", value, "status");
//            return (Criteria) this;
//        }
//
//        public Criteria andStatusLessThan(Integer value) {
//            addCriterion("status <", value, "status");
//            return (Criteria) this;
//        }
//
//        public Criteria andStatusLessThanOrEqualTo(Integer value) {
//            addCriterion("status <=", value, "status");
//            return (Criteria) this;
//        }
//
//        public Criteria andStatusIn(List<Integer> values) {
//            addCriterion("status in", values, "status");
//            return (Criteria) this;
//        }
//
//        public Criteria andStatusNotIn(List<Integer> values) {
//            addCriterion("status not in", values, "status");
//            return (Criteria) this;
//        }
//
//        public Criteria andStatusBetween(Integer value1, Integer value2) {
//            addCriterion("status between", value1, value2, "status");
//            return (Criteria) this;
//        }
//
//        public Criteria andStatusNotBetween(Integer value1, Integer value2) {
//            addCriterion("status not between", value1, value2, "status");
//            return (Criteria) this;
//        }
//
//        public Criteria andTaskTypeIsNull() {
//            addCriterion("task_type is null");
//            return (Criteria) this;
//        }
//
//        public Criteria andTaskTypeIsNotNull() {
//            addCriterion("task_type is not null");
//            return (Criteria) this;
//        }
//
//        public Criteria andTaskTypeEqualTo(String value) {
//            addCriterion("task_type =", value, "taskType");
//            return (Criteria) this;
//        }
//
//        public Criteria andTaskTypeNotEqualTo(String value) {
//            addCriterion("task_type <>", value, "taskType");
//            return (Criteria) this;
//        }
//
//        public Criteria andTaskTypeGreaterThan(String value) {
//            addCriterion("task_type >", value, "taskType");
//            return (Criteria) this;
//        }
//
//        public Criteria andTaskTypeGreaterThanOrEqualTo(String value) {
//            addCriterion("task_type >=", value, "taskType");
//            return (Criteria) this;
//        }
//
//        public Criteria andTaskTypeLessThan(String value) {
//            addCriterion("task_type <", value, "taskType");
//            return (Criteria) this;
//        }
//
//        public Criteria andTaskTypeLessThanOrEqualTo(String value) {
//            addCriterion("task_type <=", value, "taskType");
//            return (Criteria) this;
//        }
//
//        public Criteria andTaskTypeLike(String value) {
//            addCriterion("task_type like", value, "taskType");
//            return (Criteria) this;
//        }
//
//        public Criteria andTaskTypeNotLike(String value) {
//            addCriterion("task_type not like", value, "taskType");
//            return (Criteria) this;
//        }
//
//        public Criteria andTaskTypeIn(List<String> values) {
//            addCriterion("task_type in", values, "taskType");
//            return (Criteria) this;
//        }
//
//        public Criteria andTaskTypeNotIn(List<String> values) {
//            addCriterion("task_type not in", values, "taskType");
//            return (Criteria) this;
//        }
//
//        public Criteria andTaskTypeBetween(String value1, String value2) {
//            addCriterion("task_type between", value1, value2, "taskType");
//            return (Criteria) this;
//        }
//
//        public Criteria andTaskTypeNotBetween(String value1, String value2) {
//            addCriterion("task_type not between", value1, value2, "taskType");
//            return (Criteria) this;
//        }
//
//        public Criteria andCreateAtIsNull() {
//            addCriterion("create_at is null");
//            return (Criteria) this;
//        }
//
//        public Criteria andCreateAtIsNotNull() {
//            addCriterion("create_at is not null");
//            return (Criteria) this;
//        }
//
//        public Criteria andCreateAtEqualTo(LocalDateTime value) {
//            addCriterion("create_at =", value, "createAt");
//            return (Criteria) this;
//        }
//
//        public Criteria andCreateAtNotEqualTo(LocalDateTime value) {
//            addCriterion("create_at <>", value, "createAt");
//            return (Criteria) this;
//        }
//
//        public Criteria andCreateAtGreaterThan(LocalDateTime value) {
//            addCriterion("create_at >", value, "createAt");
//            return (Criteria) this;
//        }
//
//        public Criteria andCreateAtGreaterThanOrEqualTo(LocalDateTime value) {
//            addCriterion("create_at >=", value, "createAt");
//            return (Criteria) this;
//        }
//
//        public Criteria andCreateAtLessThan(LocalDateTime value) {
//            addCriterion("create_at <", value, "createAt");
//            return (Criteria) this;
//        }
//
//        public Criteria andCreateAtLessThanOrEqualTo(LocalDateTime value) {
//            addCriterion("create_at <=", value, "createAt");
//            return (Criteria) this;
//        }
//
//        public Criteria andCreateAtIn(List<LocalDateTime> values) {
//            addCriterion("create_at in", values, "createAt");
//            return (Criteria) this;
//        }
//
//        public Criteria andCreateAtNotIn(List<LocalDateTime> values) {
//            addCriterion("create_at not in", values, "createAt");
//            return (Criteria) this;
//        }
//
//        public Criteria andCreateAtBetween(LocalDateTime value1, LocalDateTime value2) {
//            addCriterion("create_at between", value1, value2, "createAt");
//            return (Criteria) this;
//        }
//
//        public Criteria andCreateAtNotBetween(LocalDateTime value1, LocalDateTime value2) {
//            addCriterion("create_at not between", value1, value2, "createAt");
//            return (Criteria) this;
//        }
//
//        public Criteria andUpdateAtIsNull() {
//            addCriterion("update_at is null");
//            return (Criteria) this;
//        }
//
//        public Criteria andUpdateAtIsNotNull() {
//            addCriterion("update_at is not null");
//            return (Criteria) this;
//        }
//
//        public Criteria andUpdateAtEqualTo(LocalDateTime value) {
//            addCriterion("update_at =", value, "updateAt");
//            return (Criteria) this;
//        }
//
//        public Criteria andUpdateAtNotEqualTo(LocalDateTime value) {
//            addCriterion("update_at <>", value, "updateAt");
//            return (Criteria) this;
//        }
//
//        public Criteria andUpdateAtGreaterThan(LocalDateTime value) {
//            addCriterion("update_at >", value, "updateAt");
//            return (Criteria) this;
//        }
//
//        public Criteria andUpdateAtGreaterThanOrEqualTo(LocalDateTime value) {
//            addCriterion("update_at >=", value, "updateAt");
//            return (Criteria) this;
//        }
//
//        public Criteria andUpdateAtLessThan(LocalDateTime value) {
//            addCriterion("update_at <", value, "updateAt");
//            return (Criteria) this;
//        }
//
//        public Criteria andUpdateAtLessThanOrEqualTo(LocalDateTime value) {
//            addCriterion("update_at <=", value, "updateAt");
//            return (Criteria) this;
//        }
//
//        public Criteria andUpdateAtIn(List<LocalDateTime> values) {
//            addCriterion("update_at in", values, "updateAt");
//            return (Criteria) this;
//        }
//
//        public Criteria andUpdateAtNotIn(List<LocalDateTime> values) {
//            addCriterion("update_at not in", values, "updateAt");
//            return (Criteria) this;
//        }
//
//        public Criteria andUpdateAtBetween(LocalDateTime value1, LocalDateTime value2) {
//            addCriterion("update_at between", value1, value2, "updateAt");
//            return (Criteria) this;
//        }
//
//        public Criteria andUpdateAtNotBetween(LocalDateTime value1, LocalDateTime value2) {
//            addCriterion("update_at not between", value1, value2, "updateAt");
//            return (Criteria) this;
//        }
//
//        public Criteria andIsDeletedIsNull() {
//            addCriterion("is_deleted is null");
//            return (Criteria) this;
//        }
//
//        public Criteria andIsDeletedIsNotNull() {
//            addCriterion("is_deleted is not null");
//            return (Criteria) this;
//        }
//
//        public Criteria andIsDeletedEqualTo(Byte value) {
//            addCriterion("is_deleted =", value, "isDeleted");
//            return (Criteria) this;
//        }
//
//        public Criteria andIsDeletedNotEqualTo(Byte value) {
//            addCriterion("is_deleted <>", value, "isDeleted");
//            return (Criteria) this;
//        }
//
//        public Criteria andIsDeletedGreaterThan(Byte value) {
//            addCriterion("is_deleted >", value, "isDeleted");
//            return (Criteria) this;
//        }
//
//        public Criteria andIsDeletedGreaterThanOrEqualTo(Byte value) {
//            addCriterion("is_deleted >=", value, "isDeleted");
//            return (Criteria) this;
//        }
//
//        public Criteria andIsDeletedLessThan(Byte value) {
//            addCriterion("is_deleted <", value, "isDeleted");
//            return (Criteria) this;
//        }
//
//        public Criteria andIsDeletedLessThanOrEqualTo(Byte value) {
//            addCriterion("is_deleted <=", value, "isDeleted");
//            return (Criteria) this;
//        }
//
//        public Criteria andIsDeletedIn(List<Byte> values) {
//            addCriterion("is_deleted in", values, "isDeleted");
//            return (Criteria) this;
//        }
//
//        public Criteria andIsDeletedNotIn(List<Byte> values) {
//            addCriterion("is_deleted not in", values, "isDeleted");
//            return (Criteria) this;
//        }
//
//        public Criteria andIsDeletedBetween(Byte value1, Byte value2) {
//            addCriterion("is_deleted between", value1, value2, "isDeleted");
//            return (Criteria) this;
//        }
//
//        public Criteria andIsDeletedNotBetween(Byte value1, Byte value2) {
//            addCriterion("is_deleted not between", value1, value2, "isDeleted");
//            return (Criteria) this;
//        }
//
//        public Criteria andMessageIsNull() {
//            addCriterion("message is null");
//            return (Criteria) this;
//        }
//
//        public Criteria andMessageIsNotNull() {
//            addCriterion("message is not null");
//            return (Criteria) this;
//        }
//
//        public Criteria andMessageEqualTo(String value) {
//            addCriterion("message =", value, "message");
//            return (Criteria) this;
//        }
//
//        public Criteria andMessageNotEqualTo(String value) {
//            addCriterion("message <>", value, "message");
//            return (Criteria) this;
//        }
//
//        public Criteria andMessageGreaterThan(String value) {
//            addCriterion("message >", value, "message");
//            return (Criteria) this;
//        }
//
//        public Criteria andMessageGreaterThanOrEqualTo(String value) {
//            addCriterion("message >=", value, "message");
//            return (Criteria) this;
//        }
//
//        public Criteria andMessageLessThan(String value) {
//            addCriterion("message <", value, "message");
//            return (Criteria) this;
//        }
//
//        public Criteria andMessageLessThanOrEqualTo(String value) {
//            addCriterion("message <=", value, "message");
//            return (Criteria) this;
//        }
//
//        public Criteria andMessageLike(String value) {
//            addCriterion("message like", value, "message");
//            return (Criteria) this;
//        }
//
//        public Criteria andMessageNotLike(String value) {
//            addCriterion("message not like", value, "message");
//            return (Criteria) this;
//        }
//
//        public Criteria andMessageIn(List<String> values) {
//            addCriterion("message in", values, "message");
//            return (Criteria) this;
//        }
//
//        public Criteria andMessageNotIn(List<String> values) {
//            addCriterion("message not in", values, "message");
//            return (Criteria) this;
//        }
//
//        public Criteria andMessageBetween(String value1, String value2) {
//            addCriterion("message between", value1, value2, "message");
//            return (Criteria) this;
//        }
//
//        public Criteria andMessageNotBetween(String value1, String value2) {
//            addCriterion("message not between", value1, value2, "message");
//            return (Criteria) this;
//        }
//
//        public Criteria andResultIsNull() {
//            addCriterion("result is null");
//            return (Criteria) this;
//        }
//
//        public Criteria andResultIsNotNull() {
//            addCriterion("result is not null");
//            return (Criteria) this;
//        }
//
//        public Criteria andResultEqualTo(String value) {
//            addCriterion("result =", value, "result");
//            return (Criteria) this;
//        }
//
//        public Criteria andResultNotEqualTo(String value) {
//            addCriterion("result <>", value, "result");
//            return (Criteria) this;
//        }
//
//        public Criteria andResultGreaterThan(String value) {
//            addCriterion("result >", value, "result");
//            return (Criteria) this;
//        }
//
//        public Criteria andResultGreaterThanOrEqualTo(String value) {
//            addCriterion("result >=", value, "result");
//            return (Criteria) this;
//        }
//
//        public Criteria andResultLessThan(String value) {
//            addCriterion("result <", value, "result");
//            return (Criteria) this;
//        }
//
//        public Criteria andResultLessThanOrEqualTo(String value) {
//            addCriterion("result <=", value, "result");
//            return (Criteria) this;
//        }
//
//        public Criteria andResultLike(String value) {
//            addCriterion("result like", value, "result");
//            return (Criteria) this;
//        }
//
//        public Criteria andResultNotLike(String value) {
//            addCriterion("result not like", value, "result");
//            return (Criteria) this;
//        }
//
//        public Criteria andResultIn(List<String> values) {
//            addCriterion("result in", values, "result");
//            return (Criteria) this;
//        }
//
//        public Criteria andResultNotIn(List<String> values) {
//            addCriterion("result not in", values, "result");
//            return (Criteria) this;
//        }
//
//        public Criteria andResultBetween(String value1, String value2) {
//            addCriterion("result between", value1, value2, "result");
//            return (Criteria) this;
//        }
//
//        public Criteria andResultNotBetween(String value1, String value2) {
//            addCriterion("result not between", value1, value2, "result");
//            return (Criteria) this;
//        }
//    }
//
//    public static class Criteria extends GeneratedCriteria {
//
//        protected Criteria() {
//            super();
//        }
//    }
//
//    public static class Criterion {
//        private String condition;
//
//        private Object value;
//
//        private Object secondValue;
//
//        private boolean noValue;
//
//        private boolean singleValue;
//
//        private boolean betweenValue;
//
//        private boolean listValue;
//
//        private String typeHandler;
//
//        public String getCondition() {
//            return condition;
//        }
//
//        public Object getValue() {
//            return value;
//        }
//
//        public Object getSecondValue() {
//            return secondValue;
//        }
//
//        public boolean isNoValue() {
//            return noValue;
//        }
//
//        public boolean isSingleValue() {
//            return singleValue;
//        }
//
//        public boolean isBetweenValue() {
//            return betweenValue;
//        }
//
//        public boolean isListValue() {
//            return listValue;
//        }
//
//        public String getTypeHandler() {
//            return typeHandler;
//        }
//
//        protected Criterion(String condition) {
//            super();
//            this.condition = condition;
//            this.typeHandler = null;
//            this.noValue = true;
//        }
//
//        protected Criterion(String condition, Object value, String typeHandler) {
//            super();
//            this.condition = condition;
//            this.value = value;
//            this.typeHandler = typeHandler;
//            if (value instanceof List<?>) {
//                this.listValue = true;
//            } else {
//                this.singleValue = true;
//            }
//        }
//
//        protected Criterion(String condition, Object value) {
//            this(condition, value, null);
//        }
//
//        protected Criterion(String condition, Object value, Object secondValue, String typeHandler) {
//            super();
//            this.condition = condition;
//            this.value = value;
//            this.secondValue = secondValue;
//            this.typeHandler = typeHandler;
//            this.betweenValue = true;
//        }
//
//        protected Criterion(String condition, Object value, Object secondValue) {
//            this(condition, value, secondValue, null);
//        }
//    }
//}