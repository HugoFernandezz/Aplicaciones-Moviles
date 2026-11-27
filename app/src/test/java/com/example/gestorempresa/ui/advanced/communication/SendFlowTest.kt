package com.example.gestorempresa.ui.advanced.communication

import org.junit.Assert.*
import org.junit.Test

class SendFlowTest {
    @Test fun bothChannelsAreOpenedOneAfterTheOther() {
        var plan = planFor(Channel.BOTH)
        assertEquals(listOf(Channel.WHATSAPP, Channel.EMAIL), plan.steps.map { it.channel })
        assertEquals(Channel.WHATSAPP, plan.current?.channel)
        plan = plan.update(Channel.WHATSAPP, SendStatus.OPENED)
        // Abierto no es enviado: el paso sigue en curso hasta que el usuario responda.
        assertEquals(Channel.WHATSAPP, plan.current?.channel)
        plan = plan.update(Channel.WHATSAPP, SendStatus.CONFIRMED)
        assertEquals(Channel.EMAIL, plan.current?.channel)
        plan = plan.update(Channel.EMAIL, SendStatus.NOT_SENT)
        assertTrue(plan.finished)
        assertEquals("WhatsApp: Enviado (confirmado por ti) · Email: No enviado", plan.summary())
    }

    @Test fun aFailedChannelDoesNotBlockTheNextOne() {
        val plan = planFor(Channel.BOTH).update(Channel.WHATSAPP, SendStatus.FAILED, "El teléfono no es válido para WhatsApp.")
        assertEquals(Channel.EMAIL, plan.current?.channel)
        assertEquals(1, planFor(Channel.EMAIL).steps.size)
    }

    @Test fun neverLabelsAnOpenedMessageAsSent() {
        assertFalse(SendStatus.OPENED.label.contains("Enviado"))
        assertTrue(SendStatus.OPENED.label.contains("no verificado"))
    }
}
