package com.garment.util;

public class PermissionConstants {

    public static final String USER_CREATE = "USER_CREATE";
    public static final String USER_READ = "USER_READ";
    public static final String USER_UPDATE = "USER_UPDATE";
    public static final String USER_DELETE = "USER_DELETE";
    public static final String ROLE_ASSIGN = "ROLE_ASSIGN";

    public static final String PLAN_CREATE = "PLAN_CREATE";
    public static final String PLAN_READ = "PLAN_READ";
    public static final String PLAN_UPDATE = "PLAN_UPDATE";
    public static final String PLAN_DELETE = "PLAN_DELETE";
    public static final String PLAN_APPROVE = "PLAN_APPROVE";
    public static final String TASK_ASSIGN = "TASK_ASSIGN";
    public static final String TASK_UPDATE = "TASK_UPDATE";

    public static final String INVENTORY_READ = "INVENTORY_READ";
    public static final String INVENTORY_IN = "INVENTORY_IN";
    public static final String INVENTORY_OUT = "INVENTORY_OUT";
    public static final String INVENTORY_ALERT = "INVENTORY_ALERT";

    public static final String ORDER_CREATE = "ORDER_CREATE";
    public static final String ORDER_READ = "ORDER_READ";
    public static final String ORDER_UPDATE = "ORDER_UPDATE";
    public static final String ORDER_CANCEL = "ORDER_CANCEL";
    public static final String ORDER_APPROVE = "ORDER_APPROVE";

    public static final String SALES_CREATE = "SALES_CREATE";
    public static final String SALES_READ = "SALES_READ";
    public static final String SALES_REPORT = "SALES_REPORT";
    public static final String CUSTOMER_MANAGE = "CUSTOMER_MANAGE";

    public static final String STATS_PRODUCTION = "STATS_PRODUCTION";
    public static final String STATS_SALES = "STATS_SALES";
    public static final String STATS_INVENTORY = "STATS_INVENTORY";

    public static final String PRODUCT_DEFINITION_CREATE = "PRODUCT_DEFINITION_CREATE";
    public static final String PRODUCT_DEFINITION_READ = "PRODUCT_DEFINITION_READ";
    public static final String PRODUCT_DEFINITION_UPDATE = "PRODUCT_DEFINITION_UPDATE";
    public static final String PRODUCT_DEFINITION_DELETE = "PRODUCT_DEFINITION_DELETE";

    public static final String[] ALL_PERMISSIONS = {
            USER_CREATE, USER_READ, USER_UPDATE, USER_DELETE, ROLE_ASSIGN,
            PLAN_CREATE, PLAN_READ, PLAN_UPDATE, PLAN_DELETE, PLAN_APPROVE, TASK_ASSIGN, TASK_UPDATE,
            INVENTORY_READ, INVENTORY_IN, INVENTORY_OUT, INVENTORY_ALERT,
            ORDER_CREATE, ORDER_READ, ORDER_UPDATE, ORDER_CANCEL, ORDER_APPROVE,
            SALES_CREATE, SALES_READ, SALES_REPORT, CUSTOMER_MANAGE,
            STATS_PRODUCTION, STATS_SALES, STATS_INVENTORY,
            PRODUCT_DEFINITION_CREATE, PRODUCT_DEFINITION_READ, PRODUCT_DEFINITION_UPDATE, PRODUCT_DEFINITION_DELETE
    };

    public static final String[] ADMIN_PERMISSIONS = ALL_PERMISSIONS;

    public static final String[] PRODUCTION_MANAGER_PERMISSIONS = {
            PLAN_CREATE, PLAN_READ, PLAN_UPDATE, PLAN_DELETE, PLAN_APPROVE, TASK_ASSIGN, TASK_UPDATE,
            INVENTORY_READ,
            PRODUCT_DEFINITION_READ,
            STATS_PRODUCTION, STATS_INVENTORY
    };

    public static final String[] WAREHOUSE_STAFF_PERMISSIONS = {
            INVENTORY_READ, INVENTORY_IN, INVENTORY_OUT, INVENTORY_ALERT,
            ORDER_READ,
            STATS_INVENTORY
    };

    public static final String[] SALES_STAFF_PERMISSIONS = {
            ORDER_CREATE, ORDER_READ, ORDER_UPDATE, ORDER_CANCEL, ORDER_APPROVE,
            SALES_CREATE, SALES_READ, SALES_REPORT, CUSTOMER_MANAGE
    };

    public static final String[] INACTIVE_PERMISSIONS = {};

    private PermissionConstants() {
    }
}
