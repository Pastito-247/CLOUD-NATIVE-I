package com.ecommerce.orders.service;

import com.ecommerce.orders.event.OrderCreatedEvent;
import com.ecommerce.orders.model.Ticket;
import com.ecommerce.orders.repository.TicketRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Logica de negocio de generacion de tickets/documentos. No conoce RabbitMQ:
 * es invocada desde el consumidor.
 */
@Service
public class TicketService {

    private static final Logger log = LoggerFactory.getLogger(TicketService.class);
    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final TicketRepository ticketRepository;

    public TicketService(TicketRepository ticketRepository) {
        this.ticketRepository = ticketRepository;
    }

    public Ticket generateTicket(OrderCreatedEvent event) {
        if (event == null || event.orderId() == null) {
            throw new IllegalArgumentException("Evento de pedido invalido: sin orderId");
        }

        String content = """
                =============================================
                TUKI-TECH - TICKET DE PEDIDO
                =============================================
                Pedido N°      : %d
                Cliente        : %s
                Total          : $%s
                Estado         : %s
                Direccion      : %s
                Emitido        : %s
                =============================================
                """.formatted(
                event.orderId(),
                event.userEmail(),
                event.total(),
                event.status(),
                event.shippingAddress(),
                LocalDateTime.now().format(FORMATTER));

        Ticket ticket = new Ticket(event.orderId(), event.userEmail(), event.total(), content);
        Ticket saved = ticketRepository.save(ticket);
        log.info("Ticket {} generado para el pedido {}", saved.getId(), event.orderId());
        return saved;
    }
}
