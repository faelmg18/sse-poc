package com.example.ssepoc.data

import com.google.gson.annotations.SerializedName

data class CheckoutEvent(
    val id: String?,
    val referenceId: String?,
    val ownerName: String?,
    val transactionId: String?,
    val status: String?,
    val previousStatus: String?,
    val errorInfo: String?,
    val paymentInfo: PaymentInfo?,
    val pricingReferenceId: String?,
    val amount: Double?,
    val discountValue: Double?,
    val discountType: String?,
    val totalAmount: Double?,
    val itemsQuantity: Int?,
    val createdAt: String?,
    val updatedAt: String?,
    val type: String?,
)

data class PaymentInfo(
    val method: String?,
    val amount: Double?,
    val discountType: String?,
    val discountAmount: Double?,
    val amountToPay: Double?,
    val currency: String?,
    val paymentDocument: String?,
    val dueDate: String?,
    val boletoNumber: String?,
    val boletoReference: String?,
    val cardBrand: String?,
    val pixEmv: String?,
    val registerDate: String?,
    val installments: Int?,
    val pspReference: String?,
    val player: String?,
    val shippingId: String?,
    val pixExpirationTimeInSeconds: Int?,
)
