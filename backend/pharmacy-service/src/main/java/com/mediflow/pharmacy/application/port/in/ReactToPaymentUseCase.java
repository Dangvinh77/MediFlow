package com.mediflow.pharmacy.application.port.in;

import com.mediflow.pharmacy.application.dto.command.PaymentCompletedCommand;

/**
 * In-port — "phản ứng khi có tin đã thanh toán". Saga quy định chỉ được xuất thuốc
 * SAU khi bệnh nhân trả tiền; event {@code payment.completed} do billing publish chính
 * là tín hiệu đó. Driving adapter gọi in-port này là consumer trong {@code messaging/consumer}.
 *
 * <p>Đây chỉ là hợp đồng — {@code PharmacyApplicationService} sẽ hiện thực. Application
 * không đụng tới RabbitMQ: port nhận application command, không nhận event AMQP thô.
 */
public interface ReactToPaymentUseCase {

    /**
     * Xử lý tin "đơn {prescriptionId} đã được thanh toán". Chống xử lý trùng qua
     * {@code ProcessedEventPort} theo eventId (BR-D9): RabbitMQ gửi lại cùng một tin thì
     * lần sau bị bỏ qua. Gọi lại {@code DispensePrescriptionUseCase.dispense} với người
     * thực hiện là hệ thống.
     *
     * <p>Nếu xuất thất bại, use case xuất thuốc ghi phiếu {@code FAILED} và phát
     * {@code prescription.dispense.failed} trước khi exception đi ra driving adapter.
     * Consumer/container chịu trách nhiệm áp dụng chính sách retry và dead-letter.
     *
     * @param command dữ liệu thanh toán đã được driving adapter chuyển vào application
     */
    void onPaymentCompleted(PaymentCompletedCommand command);
}
