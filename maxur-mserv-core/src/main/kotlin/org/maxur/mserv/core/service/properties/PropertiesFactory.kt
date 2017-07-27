@file:Suppress("unused")

package org.maxur.mserv.core.service.properties

import com.fasterxml.jackson.core.JsonFactory
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory
import org.jvnet.hk2.annotations.Contract
import org.jvnet.hk2.annotations.Service
import org.maxur.mserv.core.utils.Either
import org.maxur.mserv.core.utils.either
import java.net.URI

/**
 * @author myunusov
 * @version 1.0
 * @since <pre>24.06.2017</pre>
 */
@Contract
abstract class PropertiesFactory {
    abstract fun make(source: PropertiesSource): Either<Exception, Properties>
}

@Service(name = "None")
class PropertiesFactoryNullImpl : PropertiesFactory() {
    override fun make(source: PropertiesSource): Either<Exception, Properties> = either { nullProperties(source) }

    private fun nullProperties(source: PropertiesSource): Properties =
            if (source.isConfigured())
                throw IllegalArgumentException("None properties source is configured")
            else
                object : Properties {
                    override fun asString(key: String): String? = error(key)
                    override fun asLong(key: String): Long? = error(key)
                    override fun asInteger(key: String): Int? = error(key)
                    override fun asURI(key: String): URI? = error(key)
                    override fun <P> read(key: String, clazz: Class<P>): P? = error(key)
                }

    private fun <T> error(key: String): T =
            throw IllegalStateException("Service Configuration is not found. Key '$key' unresolved")

}


@Service(name = "Json")
class PropertiesFactoryJsonImpl : PropertiesFactory() {
    override fun make(source: PropertiesSource): Either<Exception, Properties>  =
            either { PropertiesSourceJacksonImpl(JsonFactory(), "json", source) }
}

@Service(name = "Yaml")
class PropertiesFactoryYamlImpl : PropertiesFactory() {
    override fun make(source: PropertiesSource): Either<Exception, Properties>  =
            either { PropertiesSourceJacksonImpl(YAMLFactory(), "yaml", source) }
}

@Service(name = "Hocon")
class PropertiesFactoryHoconImpl : PropertiesFactory() {
    override fun make(source: PropertiesSource): Either<Exception, Properties>  =
            either { PropertiesSourceHoconImpl(source) }
}

