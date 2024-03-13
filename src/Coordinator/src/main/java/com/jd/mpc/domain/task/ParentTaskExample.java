//package com.jd.mpc.domain.task;
//
//import java.time.LocalDateTime;
//import java.util.ArrayList;
//import java.util.List;
//
//public class ParentTaskExample {
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
//    parent_task
//     */
//    public ParentTaskExample() {
//        oredCriteria = new ArrayList<Criteria>();
//    }
//
//    /**
//    parent_task
//     */
//    public void setOrderByClause(String orderByClause) {
//        this.orderByClause = orderByClause;
//    }
//
//    /**
//    parent_task
//     */
//    public String getOrderByClause() {
//        return orderByClause;
//    }
//
//    /**
//    parent_task
//     */
//    public void setDistinct(boolean distinct) {
//        this.distinct = distinct;
//    }
//
//    /**
//    parent_task
//     */
//    public boolean isDistinct() {
//        return distinct;
//    }
//
//    /**
//    parent_task
//     */
//    public List<Criteria> getOredCriteria() {
//        return oredCriteria;
//    }
//
//    /**
//    parent_task
//     */
//    public void or(Criteria criteria) {
//        oredCriteria.add(criteria);
//    }
//
//    /**
//    parent_task
//     */
//    public Criteria or() {
//        Criteria criteria = createCriteriaInternal();
//        oredCriteria.add(criteria);
//        return criteria;
//    }
//
//    /**
//    parent_task
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
//    parent_task
//     */
//    protected Criteria createCriteriaInternal() {
//        Criteria criteria = new Criteria();
//        return criteria;
//    }
//
//    /**
//    parent_task
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
//    parent_task
//     */
//    public void setOffset(Integer offset) {
//        this.offset = offset;
//    }
//
//    /**
//    parent_task
//     */
//    public Integer getOffset() {
//        return this.offset;
//    }
//
//    /**
//    parent_task
//     */
//    public void setRows(Integer rows) {
//        this.rows = rows;
//    }
//
//    /**
//    parent_task
//     */
//    public Integer getRows() {
//        return this.rows;
//    }
//
//    /**
//    parent_task
//     */
//    public ParentTaskExample limit(Integer rows) {
//        this.rows = rows;
//        return this;
//    }
//
//    /**
//    parent_task
//     */
//    public ParentTaskExample limit(Integer offset, Integer rows) {
//        this.offset = offset;
//        this.rows = rows;
//        return this;
//    }
//
//    /**
//    parent_task
//     */
//    public ParentTaskExample page(Integer page, Integer pageSize) {
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
//        public Criteria andTaskIdIsNull() {
//            addCriterion("task_id is null");
//            return (Criteria) this;
//        }
//
//        public Criteria andTaskIdIsNotNull() {
//            addCriterion("task_id is not null");
//            return (Criteria) this;
//        }
//
//        public Criteria andTaskIdEqualTo(String value) {
//            addCriterion("task_id =", value, "taskId");
//            return (Criteria) this;
//        }
//
//        public Criteria andTaskIdNotEqualTo(String value) {
//            addCriterion("task_id <>", value, "taskId");
//            return (Criteria) this;
//        }
//
//        public Criteria andTaskIdGreaterThan(String value) {
//            addCriterion("task_id >", value, "taskId");
//            return (Criteria) this;
//        }
//
//        public Criteria andTaskIdGreaterThanOrEqualTo(String value) {
//            addCriterion("task_id >=", value, "taskId");
//            return (Criteria) this;
//        }
//
//        public Criteria andTaskIdLessThan(String value) {
//            addCriterion("task_id <", value, "taskId");
//            return (Criteria) this;
//        }
//
//        public Criteria andTaskIdLessThanOrEqualTo(String value) {
//            addCriterion("task_id <=", value, "taskId");
//            return (Criteria) this;
//        }
//
//        public Criteria andTaskIdLike(String value) {
//            addCriterion("task_id like", value, "taskId");
//            return (Criteria) this;
//        }
//
//        public Criteria andTaskIdNotLike(String value) {
//            addCriterion("task_id not like", value, "taskId");
//            return (Criteria) this;
//        }
//
//        public Criteria andTaskIdIn(List<String> values) {
//            addCriterion("task_id in", values, "taskId");
//            return (Criteria) this;
//        }
//
//        public Criteria andTaskIdNotIn(List<String> values) {
//            addCriterion("task_id not in", values, "taskId");
//            return (Criteria) this;
//        }
//
//        public Criteria andTaskIdBetween(String value1, String value2) {
//            addCriterion("task_id between", value1, value2, "taskId");
//            return (Criteria) this;
//        }
//
//        public Criteria andTaskIdNotBetween(String value1, String value2) {
//            addCriterion("task_id not between", value1, value2, "taskId");
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
//        public Criteria andTypeIsNull() {
//            addCriterion("type is null");
//            return (Criteria) this;
//        }
//
//        public Criteria andTypeIsNotNull() {
//            addCriterion("type is not null");
//            return (Criteria) this;
//        }
//
//        public Criteria andTypeEqualTo(String value) {
//            addCriterion("type =", value, "type");
//            return (Criteria) this;
//        }
//
//        public Criteria andTypeNotEqualTo(String value) {
//            addCriterion("type <>", value, "type");
//            return (Criteria) this;
//        }
//
//        public Criteria andTypeGreaterThan(String value) {
//            addCriterion("type >", value, "type");
//            return (Criteria) this;
//        }
//
//        public Criteria andTypeGreaterThanOrEqualTo(String value) {
//            addCriterion("type >=", value, "type");
//            return (Criteria) this;
//        }
//
//        public Criteria andTypeLessThan(String value) {
//            addCriterion("type <", value, "type");
//            return (Criteria) this;
//        }
//
//        public Criteria andTypeLessThanOrEqualTo(String value) {
//            addCriterion("type <=", value, "type");
//            return (Criteria) this;
//        }
//
//        public Criteria andTypeLike(String value) {
//            addCriterion("type like", value, "type");
//            return (Criteria) this;
//        }
//
//        public Criteria andTypeNotLike(String value) {
//            addCriterion("type not like", value, "type");
//            return (Criteria) this;
//        }
//
//        public Criteria andTypeIn(List<String> values) {
//            addCriterion("type in", values, "type");
//            return (Criteria) this;
//        }
//
//        public Criteria andTypeNotIn(List<String> values) {
//            addCriterion("type not in", values, "type");
//            return (Criteria) this;
//        }
//
//        public Criteria andTypeBetween(String value1, String value2) {
//            addCriterion("type between", value1, value2, "type");
//            return (Criteria) this;
//        }
//
//        public Criteria andTypeNotBetween(String value1, String value2) {
//            addCriterion("type not between", value1, value2, "type");
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