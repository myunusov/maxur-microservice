package org.maxur.mserv.core.service.msbuilder

import org.maxur.mserv.core.MicroService
import org.maxur.mserv.core.domain.BaseService
import org.maxur.mserv.core.domain.Holder
import java.util.function.Consumer


class Java {
    companion object dsl {
        fun service(): JBuilder = JBuilder()
    }
}


interface IJBuilder {
    fun title(value: String): JBuilder
    fun packages(value: String): JBuilder
    fun properties(format: String): JPropertiesBuilder
    fun properties(): JBuilder
    fun withoutProperties(): JBuilder
    fun service(type: String, properties: String): JBuilder
    fun rest(): JBuilder
    fun beforeStart(func: Consumer<in BaseService>): JBuilder
    fun afterStop(func: Consumer<in BaseService>): JBuilder
    fun beforeStop(func: Consumer<in BaseService>): JBuilder
    fun afterStart(func: Consumer<in BaseService>): JBuilder
    fun onError(func: Consumer<Exception>): JBuilder
    fun build(): MicroService
    fun start()
}

class JBuilder : MSBuilder(), IJBuilder {

    override fun title(value: String): JBuilder {
        titleHolder = Holder.string(value)
        return this
    }

    override fun packages(value: String): JBuilder {
        packagesHolder.addAll(value.split("\\s*,\\s*"))
        return this
    }

    override fun properties(format: String): JPropertiesBuilder {
        propertiesHolder = PropertiesHolder()
        propertiesHolder.format = format
        return JPropertiesBuilder(this)
    }

    override fun properties(): JBuilder {
        propertiesHolder = PropertiesHolder()
        return this
    }

    override fun withoutProperties(): JBuilder {
        propertiesHolder = PropertiesHolder()
        propertiesHolder.format = "None"
        propertiesHolder.rootKey = null
        propertiesHolder.uri = null
        return this
    }


    override fun service(type: String, properties: String): JBuilder {
        val holder = ServiceHolder()
        holder.type = type
        holder.properties = properties
        services.add(holder)
        return this
    }

    override fun rest(): JBuilder {
        service("grizzly", ":webapp")
        return this
    }

    override fun beforeStart(func: Consumer<in BaseService>): JBuilder {
        beforeStart.plusAssign(unitFunc(func))
        return this
    }

    override fun afterStop(func: Consumer<in BaseService>): JBuilder {
        afterStop.plusAssign(unitFunc(func))
        return this
    }

    override fun beforeStop(func: Consumer<in BaseService>): JBuilder {
        beforeStop.plusAssign(unitFunc(func))
        return this
    }

    override fun afterStart(func: Consumer<in BaseService>): JBuilder {
        afterStart.plusAssign(unitFunc(func))
        return this
    }

    override fun onError(func: Consumer<Exception>): JBuilder {
        onError.plusAssign(errorFunc(func))
        return this
    }

    private fun unitFunc(func: Consumer<in BaseService>): Function1<BaseService, Unit> =
            object : Function1<BaseService, Unit> {
                override fun invoke(service: BaseService) = func.accept(service)
            }

    private fun errorFunc(func: Consumer<Exception>): Function1<Exception, Unit> =
            object : Function1<Exception, Unit> {
                override fun invoke(e: Exception) = func.accept(e)
            }

    override fun start() {
        build().start()
    }


}

class JPropertiesBuilder(val parent: JBuilder) : IJBuilder by parent {

    fun url(value: String): JPropertiesBuilder {
        parent.propertiesHolder.url = value
        return this
    }

    fun rootKey(value: String): JPropertiesBuilder {
        parent.propertiesHolder.rootKey = value
        return this
    }

}

