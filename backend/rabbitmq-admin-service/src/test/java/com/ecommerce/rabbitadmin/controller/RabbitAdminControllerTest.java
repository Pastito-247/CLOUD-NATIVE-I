package com.ecommerce.rabbitadmin.controller;

import com.ecommerce.rabbitadmin.dto.QueueInfo;
import com.ecommerce.rabbitadmin.exception.InvalidRabbitResourceException;
import com.ecommerce.rabbitadmin.exception.ResourceNotFoundException;
import com.ecommerce.rabbitadmin.service.RabbitAdminService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Pruebas de la capa web del administrador de RabbitMQ: validacion de entrada
 * y mapeo de errores al contrato HTTP. La seguridad se desactiva aqui
 * (addFilters = false) para aislar el comportamiento del controlador; la
 * autorizacion se cubre en {@link RabbitAdminControllerSecurityTest}.
 */
@WebMvcTest(controllers = RabbitAdminController.class)
@AutoConfigureMockMvc(addFilters = false)
class RabbitAdminControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private RabbitAdminService rabbitAdminService;

    @Test
    void crearCola_valida_devuelve201() throws Exception {
        mockMvc.perform(post("/api/rabbit/queues")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"demo.queue","durable":true}
                                """))
                .andExpect(status().isCreated());

        verify(rabbitAdminService).createQueue(any());
    }

    @Test
    void crearCola_sinNombre_devuelve400ConDetalle() throws Exception {
        mockMvc.perform(post("/api/rabbit/queues")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.error").value("Error de validacion"))
                .andExpect(jsonPath("$.detail").value(org.hamcrest.Matchers.containsString("name")));

        verify(rabbitAdminService, never()).createQueue(any());
    }

    @Test
    void crearExchange_recursoInvalido_devuelve400() throws Exception {
        doThrow(new InvalidRabbitResourceException("El prefijo 'amq.' esta reservado por RabbitMQ"))
                .when(rabbitAdminService).createExchange(any());

        mockMvc.perform(post("/api/rabbit/exchanges")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"demo.exchange","type":"topic"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Solicitud invalida"));
    }

    @Test
    void listarColas_devuelve200ConLista() throws Exception {
        when(rabbitAdminService.listQueues()).thenReturn(List.of(
                new QueueInfo("demo.queue", true, false, "classic", "running", 0, 0, null)));

        mockMvc.perform(get("/api/rabbit/queues"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].name").value("demo.queue"));
    }

    @Test
    void eliminarColaInexistente_devuelve404() throws Exception {
        doThrow(new ResourceNotFoundException("No existe la cola 'no.existe'"))
                .when(rabbitAdminService).deleteQueue(eq("no.existe"));

        mockMvc.perform(delete("/api/rabbit/queues/no.existe"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("Recurso no encontrado"));
    }

    @Test
    void eliminarColaExistente_devuelve204() throws Exception {
        mockMvc.perform(delete("/api/rabbit/queues/demo.queue"))
                .andExpect(status().isNoContent());

        verify(rabbitAdminService).deleteQueue("demo.queue");
    }
}
