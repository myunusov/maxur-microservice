package org.maxur.mserv.core.builder

import org.maxur.mserv.core.core.Builder
import org.maxur.mserv.core.core.CompositeBuilder
import org.maxur.mserv.core.core.ErrorResult
import org.maxur.mserv.core.core.Result
import org.maxur.mserv.core.core.Value
import org.maxur.mserv.core.kotlin.Locator
import org.maxur.mserv.core.service.properties.CompositeProperties
import org.maxur.mserv.core.service.properties.Properties
import org.maxur.mserv.core.service.properties.PropertiesFactory
import org.maxur.mserv.core.service.properties.PropertiesSource
import java.net.URI

/**
 * The Properties Source Builder.
 *
 * @author Maxim Yunusov
 * @version 1.0
 * @since <pre>11/25/13</pre>
 */
sealed class PropertiesBuilder : Builder<Properties?> {

    /**
     * Base Properties Builder.
     */
    class BasePropertiesBuilder : PropertiesBuilder() {
        /** The property source format (Mandatory) */
        var format: String?  = null
            get() = field?.toLowerCase()
            set(value) { field = value }
        /** the property source url. It's Optional */
        var url: String? = null
        /** The root key of service property. It's Optional.*/
        var rootKey: String? = null

        private var uri: URI? = null
            get() = url?.let { URI.create(url) }

        /** {@inheritDoc} */
        override fun build(locator: Locator): Properties {
            return locator.locate(PropertiesFactory::class, format)
                                .make(object : PropertiesSource(format, uri, rootKey) {})
                                .result()
        }
        
        private fun <E : Throwable, V> Result<E, V>.result(): V = when (this) {
            is Value -> value
            is ErrorResult -> throw when (error) {
                is IllegalStateException -> error
                else -> IllegalStateException(error)
            }
        }
    }

    /**
     * Null Properties Builder.
     */
    object NullPropertiesBuilder : PropertiesBuilder() {
        /** {@inheritDoc} */
        override fun build(locator: Locator): Properties = PropertiesSource.nothing()
    }

}

/**
 * Composite properties builder
 */
class CompositePropertiesBuilder : CompositeBuilder<Properties>() {

    /** {@inheritDoc} */
    override fun build(locator: Locator) = when {
        list.isEmpty() -> PropertiesSource.default()
        list.all { it is PropertiesBuilder.NullPropertiesBuilder } ->
            PropertiesSource.nothing()
        else -> {
            val sources = buildListWith(locator, { item -> item is PropertiesBuilder.BasePropertiesBuilder })
            if (sources.isEmpty())
                PropertiesSource.nothing()
            else
                CompositeProperties(sources)
        }
    }
}