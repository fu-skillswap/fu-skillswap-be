# -*- coding: utf-8 -*-
import re

def process_file(filepath):
    with open(filepath, 'r', encoding='utf-8') as f:
        content = f.read()

    # Add TransactionTemplate import
    if 'org.springframework.transaction.support.TransactionTemplate' not in content:
        content = re.sub(r'(import org.springframework.transaction.annotation.Transactional;)', r'\1\nimport org.springframework.transaction.support.TransactionTemplate;', content)

    # Add transactionTemplate field
    if 'private final TransactionTemplate transactionTemplate;' not in content:
        content = re.sub(r'(private final InternalTelemetryService internalTelemetryService;)', r'\1\n    private final TransactionTemplate transactionTemplate;', content)

    # 1. Decouple checkout
    if '@Transactional\n    public PaymentCheckoutResponse checkout' in content:
        content = content.replace('@Transactional\n    public PaymentCheckoutResponse checkout', 'public PaymentCheckoutResponse checkout')
        
        old_part = '''        Booking booking = bookingRepository.findByIdForSessionUpdate(request.bookingId())
                .orElseThrow(() -> new BaseException(ErrorCode.NOT_FOUND, "Không tìm th?y booking"));'''
        new_part = '''        PaymentOrder savedOrder = transactionTemplate.execute(status -> {
            Booking booking = bookingRepository.findByIdForSessionUpdate(request.bookingId())
                    .orElseThrow(() -> new BaseException(ErrorCode.NOT_FOUND, "Không tìm th?y booking"));'''
        content = content.replace(old_part, new_part)
        
        old_part2 = '''        PaymentOrder savedOrder = paymentOrderRepository.save(draftOrder);
        int nextAttemptNo = (int) paymentAttemptRepository.countByPaymentOrderId(savedOrder.getId()) + 1;'''
        new_part2 = '''        return paymentOrderRepository.save(draftOrder);
        });

        int nextAttemptNo = (int) paymentAttemptRepository.countByPaymentOrderId(savedOrder.getId()) + 1;
        Booking booking = bookingRepository.findById(request.bookingId()).orElseThrow();
        '''
        content = content.replace(old_part2, new_part2)
        
        old_part3 = '''        PaymentAttempt attempt;
        if (savedOrder.getRemainingPayableScoin() > 0) {
            long providerOrderCode = generateProviderOrderCode(savedOrder.getId(), nextAttemptNo);
            PayOsGateway.CreatePaymentLinkResult createResult = payOsGateway.createPaymentLink(
                    buildCreatePaymentLinkCommand(booking, savedOrder, providerOrderCode)
            );
            savedOrder.setProviderOrderCode(createResult.providerOrderCode());
            savedOrder.setProviderPaymentLinkId(createResult.providerPaymentLinkId());
            savedOrder.setProviderStatus(createResult.providerStatus());
            savedOrder.setPaymentLink(createResult.checkoutUrl());
            savedOrder.setExpiresAt(createResult.expiresAt());
            savedOrder = paymentOrderRepository.save(savedOrder);

            attempt = paymentAttemptRepository.save(PaymentAttempt.builder()
                    .paymentOrderId(savedOrder.getId())
                    .attemptNo(nextAttemptNo)
                    .status(PaymentAttemptStatus.REDIRECTED)
                    .providerOrderCode(createResult.providerOrderCode())
                    .providerPaymentLinkId(createResult.providerPaymentLinkId())
                    .providerStatus(createResult.providerStatus())
                    .checkoutUrl(createResult.checkoutUrl())
                    .build());
        } else {
            savedOrder.setProviderOrderCode(null);
            savedOrder.setProviderPaymentLinkId(null);
            savedOrder.setProviderStatus("PAID");
            savedOrder.setPaymentLink(null);
            savedOrder.setExpiresAt(null);
            savedOrder = paymentOrderRepository.save(savedOrder);
            attempt = paymentAttemptRepository.save(PaymentAttempt.builder()
                    .paymentOrderId(savedOrder.getId())
                    .attemptNo(nextAttemptNo)
                    .status(PaymentAttemptStatus.SUCCEEDED)
                    .providerStatus("PAID")
                    .build());
        }

        if (coupon != null) {
            couponService.reserveCoupon(coupon, savedOrder.getId(), currentUserId, couponDiscountScoin);
        }
        if (savedOrder.getStatus() == PaymentOrderStatus.PAID) {
            finalizeInternalPayment(savedOrder, attempt, null, null, "PAID", booking);
            savedOrder = paymentOrderRepository.save(savedOrder);
        }
        return toResponse(savedOrder, attempt);'''
        
        new_part3 = '''        PayOsGateway.CreatePaymentLinkResult createResult = null;
        long providerOrderCode = 0;
        if (savedOrder.getRemainingPayableScoin() > 0) {
            providerOrderCode = generateProviderOrderCode(savedOrder.getId(), nextAttemptNo);
            createResult = payOsGateway.createPaymentLink(
                    buildCreatePaymentLinkCommand(booking, savedOrder, providerOrderCode)
            );
        }
        
        final PayOsGateway.CreatePaymentLinkResult finalCreateResult = createResult;
        final long finalProviderOrderCode = providerOrderCode;
        
        return transactionTemplate.execute(status -> {
            PaymentOrder orderToUpdate = paymentOrderRepository.findByIdForUpdate(savedOrder.getId()).orElseThrow();
            PaymentAttempt attempt;
            if (orderToUpdate.getRemainingPayableScoin() > 0) {
                orderToUpdate.setProviderOrderCode(finalCreateResult.providerOrderCode());
                orderToUpdate.setProviderPaymentLinkId(finalCreateResult.providerPaymentLinkId());
                orderToUpdate.setProviderStatus(finalCreateResult.providerStatus());
                orderToUpdate.setPaymentLink(finalCreateResult.checkoutUrl());
                orderToUpdate.setExpiresAt(finalCreateResult.expiresAt());
                orderToUpdate = paymentOrderRepository.save(orderToUpdate);

                attempt = paymentAttemptRepository.save(PaymentAttempt.builder()
                        .paymentOrderId(orderToUpdate.getId())
                        .attemptNo(nextAttemptNo)
                        .status(PaymentAttemptStatus.REDIRECTED)
                        .providerOrderCode(finalCreateResult.providerOrderCode())
                        .providerPaymentLinkId(finalCreateResult.providerPaymentLinkId())
                        .providerStatus(finalCreateResult.providerStatus())
                        .checkoutUrl(finalCreateResult.checkoutUrl())
                        .build());
            } else {
                orderToUpdate.setProviderOrderCode(null);
                orderToUpdate.setProviderPaymentLinkId(null);
                orderToUpdate.setProviderStatus("PAID");
                orderToUpdate.setPaymentLink(null);
                orderToUpdate.setExpiresAt(null);
                orderToUpdate = paymentOrderRepository.save(orderToUpdate);
                attempt = paymentAttemptRepository.save(PaymentAttempt.builder()
                        .paymentOrderId(orderToUpdate.getId())
                        .attemptNo(nextAttemptNo)
                        .status(PaymentAttemptStatus.SUCCEEDED)
                        .providerStatus("PAID")
                        .build());
            }
            
            if (request.couponCode() != null) {
                Coupon c = couponService.resolveCoupon(request.couponCode());
                if (c != null) {
                    couponService.reserveCoupon(c, orderToUpdate.getId(), currentUserId, orderToUpdate.getCouponDiscountScoin());
                }
            }
            if (orderToUpdate.getStatus() == PaymentOrderStatus.PAID) {
                Booking lockedBooking = bookingRepository.findByIdForSessionUpdate(orderToUpdate.getBookingId()).orElseThrow();
                finalizeInternalPayment(orderToUpdate, attempt, null, null, "PAID", lockedBooking);
                orderToUpdate = paymentOrderRepository.save(orderToUpdate);
            }
            return toResponse(orderToUpdate, attempt);
        });'''
        content = content.replace(old_part3, new_part3)

    # 2. Double-checked locking in handleWebhook
    if '@Transactional\n    public PaymentCheckoutResponse handleWebhook' in content:
        content = content.replace('@Transactional\n    public PaymentCheckoutResponse handleWebhook', 'public PaymentCheckoutResponse handleWebhook')
        
        old_webhook_part = '''        PaymentAttempt attempt = paymentAttemptRepository.findByProviderOrderCodeForUpdate(verified.providerOrderCode())
                .orElseThrow(() -> new BaseException(ErrorCode.NOT_FOUND,
                        "Không tìm th?y payment attempt tuong ?ng v?i orderCode PayOS"));'''
        new_webhook_part = '''        PaymentAttempt optimisticAttempt = paymentAttemptRepository.findByProviderOrderCode(verified.providerOrderCode())
                .orElseThrow(() -> new BaseException(ErrorCode.NOT_FOUND, "Không tìm th?y payment attempt"));
        if ("PAID".equals(optimisticAttempt.getProviderStatus()) || "SUCCESS".equals(optimisticAttempt.getProviderStatus()) || "00".equals(optimisticAttempt.getProviderStatus())) {
            PaymentOrder optimisticOrder = paymentOrderRepository.findById(optimisticAttempt.getPaymentOrderId()).orElseThrow();
            return toResponse(optimisticOrder, optimisticAttempt);
        }
        
        return transactionTemplate.execute(status -> {
            PaymentAttempt attempt = paymentAttemptRepository.findByProviderOrderCodeForUpdate(verified.providerOrderCode())
                    .orElseThrow(() -> new BaseException(ErrorCode.NOT_FOUND,
                            "Không tìm th?y payment attempt tuong ?ng v?i orderCode PayOS"));'''
        content = content.replace(old_webhook_part, new_webhook_part)
        
        content = content.replace('''        order = paymentOrderRepository.save(order);
        return toResponse(order, attempt);
    }''', '''        order = paymentOrderRepository.save(order);
            return toResponse(order, attempt);
        });
    }''')

    with open(filepath, 'w', encoding='utf-8') as f:
        f.write(content)

process_file('src/main/java/com/fptu/exe/skillswap/modules/payment/service/PaymentOrderService.java')
