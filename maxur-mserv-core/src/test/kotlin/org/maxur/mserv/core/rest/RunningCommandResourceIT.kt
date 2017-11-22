package org.maxur.mserv.core.rest

import com.nhaarman.mockito_kotlin.verify
import org.assertj.core.api.Assertions
import org.glassfish.hk2.utilities.binding.AbstractBinder
import org.junit.Test
import org.junit.runner.RunWith
import org.maxur.mserv.core.MicroService
import org.mockito.Mock
import org.mockito.junit.MockitoJUnitRunner
import java.io.IOException
import javax.ws.rs.client.Entity
import javax.ws.rs.core.MediaType
import kotlin.reflect.KClass

@RunWith(MockitoJUnitRunner::class)
class RunningCommandResourceIT : AbstractResourceAT() {

    @Mock
    private lateinit var service: MicroService

    override fun resourceClass(): KClass<out Any> = RunningCommandResource::class

    override fun configurator(): Function1<AbstractBinder, Unit> = { binder: AbstractBinder ->
        binder.bind(service).to(MicroService::class.java)
    }

    @Test
    @Throws(IOException::class)
    fun testServiceResourceStop() {
        val baseTarget = target("/service/command")
        val response = baseTarget.request()
                .accept(MediaType.APPLICATION_JSON)
                .post(Entity.json("{ \"type\": \"stop\" }"))
        Assertions.assertThat(response.status).isEqualTo(204)
        verify(service).deferredStop()
    }

    @Test
    @Throws(IOException::class)
    fun testServiceResourceRestart() {
        val baseTarget = target("/service/command")
        val response = baseTarget.request()
                .accept(MediaType.APPLICATION_JSON)
                .post(Entity.json("{ \"type\": \"restart\" }"))
        Assertions.assertThat(response.status).isEqualTo(204)
        verify(service).deferredRestart()
    }
}