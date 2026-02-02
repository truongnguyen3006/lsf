package com.myorg.lsf.contracts.ecommerce;

public class EcommerceEventTypes {
    private EcommerceEventTypes() {}

    public static final String ORDER_PLACED_V1 = "ecommerce.order.placed.v1";
    public static final String ORDER_VALIDATED_V1 = "ecommerce.order.validated.v1";
    public static final String ORDER_FAILED_V1 = "ecommerce.order.failed.v1";
    public static final String ORDER_PROCESSING_V1 = "ecommerce.order.processing.v1";
    public static final String ORDER_STATUS_V1 = "ecommerce.order.status.v1";

    public static final String INVENTORY_CHECK_REQUEST_V1 = "ecommerce.inventory.check.request.v1";
    public static final String INVENTORY_CHECK_RESULT_V1 = "ecommerce.inventory.check.result.v1";
    public static final String INVENTORY_ADJUSTMENT_V1 = "ecommerce.inventory.adjustment.v1";

    public static final String PAYMENT_PROCESSED_V1 = "ecommerce.payment.processed.v1";
    public static final String PAYMENT_FAILED_V1 = "ecommerce.payment.failed.v1";

    public static final String PRODUCT_CREATED_V1 = "ecommerce.product.created.v1";
    public static final String PRODUCT_CACHE_V1 = "ecommerce.product.cache.v1";
}
