/*
 * #%L
 * BroadleafCommerce Framework
 * %%
 * Copyright (C) 2009 - 2013 Broadleaf Commerce
 * %%
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *       http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * #L%
 */
/*
 * Copyright 2008-2013 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.broadleafcommerce.core.payment.service;

import org.broadleafcommerce.common.payment.PaymentGatewayType;
import org.broadleafcommerce.common.payment.PaymentType;
import org.broadleafcommerce.common.payment.dto.CreditCardDTO;
import org.broadleafcommerce.common.payment.dto.PaymentResponseDTO;
import org.broadleafcommerce.common.payment.service.PaymentGatewayCheckoutService;
import org.broadleafcommerce.common.payment.service.PaymentGatewayConfigurationService;
import org.broadleafcommerce.common.web.payment.controller.PaymentGatewayAbstractController;
import org.broadleafcommerce.core.order.domain.Order;
import org.broadleafcommerce.core.order.service.OrderService;
import org.broadleafcommerce.core.payment.domain.OrderPayment;
import org.broadleafcommerce.core.payment.domain.PaymentTransaction;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;


/**
 * Core framework implementation of the {@link PaymentGatewayCheckoutService}.
 * 
 * @see {@link PaymentGatewayAbstractController}
 * @author Phillip Verheyden (phillipuniverse)
 */
@Service("blPaymentGatewayCheckoutService")
public class DefaultPaymentGatewayCheckoutService implements PaymentGatewayCheckoutService {

    @Resource(name = "blOrderService")
    protected OrderService orderService;
    
    @Resource(name = "blOrderPaymentService")
    protected OrderPaymentService orderPaymentService;
    
    @Override
    public Long applyPaymentToOrder(PaymentResponseDTO responseDTO, PaymentGatewayConfigurationService configService) {
        
        //Payments can ONLY be parsed into PaymentInfos if they are 'valid'
        if (!responseDTO.isValid()) {
            throw new IllegalArgumentException("Invalid payment responses cannot be parsed into the order payment domain");
        }
        
        if (configService == null) {
            throw new IllegalArgumentException("Config service cannot be null");
        }
        
        Long orderId = Long.parseLong(responseDTO.getOrderId());
        Order order = orderService.findOrderById(orderId);
        
        //TODO: ensure that the order has not already been checked out before applying payments to it
        
        //TODO: fill out order.getCustomer() values for anonymous customers based on values returned from the response
        
        //TODO: support multiple payment types (GIFT_CARD, ACCOUNT_CREDIT, BANK_ACCOUNT, etc)
        PaymentType type = null;
        if (responseDTO.getCreditCard() instanceof CreditCardDTO) {
            type = PaymentType.CREDIT_CARD;
        }
        
        if (!configService.handlesMultiplePayments()) {
            PaymentGatewayType gateway = configService.getGatewayType();
            //TODO: ONLY mark payments as invalid for a particular gateway
            for (OrderPayment payment : order.getPayments()) {
                markPaymentAsInvalid(payment.getId());
            }
        }
        
        // ALWAYS create a new order payment for the payment that comes in. Invalid payments should be cleaned up by
        // invoking {@link #markPaymentaAsInvalid}.
        OrderPayment payment = orderPaymentService.create();
        payment.setType(type);
        payment.setAmount(responseDTO.getAmount());
        
        //TODO: add billing address fields to the payment response DTO
        //payment.setBillingAddress(billingAddress)
        
        //TODO: I think this reference number should be completely optional. OOB I don't think there is any reason it needs
        //to be set.
        //payment.setReferenceNumber(referenceNumber)
        
        PaymentTransaction transaction = orderPaymentService.createTransaction();
        transaction.setAmount(responseDTO.getAmount());
        transaction.setRawResponse(responseDTO.getRawResponse());
        transaction.setSuccess(responseDTO.isSuccessful());
        
        //TODO: handle payments that have to be confirmed. Scenario:
        /*
         * 1. User goes through checkout
         * 2. User submits payment to gateway which supports a 'confirmation
         * 3. User is on review order page
         * 4. User goes back and makes modifications to their cart
         * 5. The user now has an order payment in the system which has been unconfirmed and is really in this weird, invalid
         *    state.
         * 6. 
         */
        
        //TODO: get the transaction type from the response DTO
        //transaction.setType(type);
        
        //TODO: copy additional fields from payment response into payment transaction
        
        //TODO: validate that this particular type of transaction is valid to be added to the payment (there might already
        // be an AUTHORIZE transaction, for instance)
        payment.addTransaction(transaction);
        payment = orderPaymentService.save(payment);
        orderService.addPaymentToOrder(order, payment, null);
        
        return payment.getId();
    }

    @Override
    public void markPaymentAsInvalid(Long orderPaymentId) {
        // TODO delete (which archives) the given payment id
    }

    @Override
    public String initiateCheckout(Long orderId) {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public String lookupOrderNumberFromOrderId(PaymentResponseDTO responseDTO) {
        Order order = orderService.findOrderById(Long.parseLong(responseDTO.getOrderId()));
        if (order == null) {
            throw new IllegalArgumentException("An order with ID " + responseDTO.getOrderId() + " cannot be found for the" +
            		" given payment response.");
        }
        return order.getOrderNumber();
    }

}