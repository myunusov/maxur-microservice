package org.maxur.mserv.frame.runner

import org.maxur.mserv.core.Builder
import org.maxur.mserv.core.CompositeBuilder
import org.maxur.mserv.core.ErrorResult
import org.maxur.mserv.core.Result
import org.maxur.mserv.core.Value
import org.maxur.mserv.frame.kotlin.Locator
import org.maxur.mserv.frame.service.properties.CompositeProperties
import org.maxur.mserv.frame.service.properties.MapProperties
import org.maxur.mserv.frame.service.properties.Properties
import org.maxur.mserv.frame.service.properties.PropertiesFactory
import org.maxur.mserv.frame.service.properties.PropertiesSource
import java.net.URI

/**
 * The Properties Source Builder.
 *
 * @author Maxim Yunusov
 * @version 1.0
 * @since <pre>11/25/13</pre>
 */
abstract class PropertiesBuilder : Builder<Properties?> {

    /** The property source format (Mandatory) */
    var format: String? = null
        get() = field?.toLowerCase()
        set(value) {
            field = value
        }
    /** the property source url. It's Optional */
    var url: String? = null

    /** The root key of service property. It's Optional.*/
    var rootKey: String? = null

    protected var uri: URI? = null
        get() = url?.let { URI.create(url) }

    /**
     * Base Properties Builder.
     */
    class BasePropertiesBuilder : PropertiesBuilder() {

        /** {@inheritDoc} */
        override fun build(locator: Locator): Properties = locator.locate(PropertiesFactory::class, format)
                .make(uri, rootKey)
                .result()
    }

    /**
     * Null Properties Builder.
     */
    object NullPropertiesBuilder : PropertiesBuilder() {
        /** {@inheritDoc} */
        override fun build(locator: Locator): Properties = PropertiesSource.nothing()
    }

    internal fun <E : Throwable, V> Result<E, V>.result(): V = when (this) {
        is Value -> value
        is ErrorResult -> throw when (error) {
            is IllegalStateException -> error
            else -> IllegalStateException(error)
        }
    }
}

/**
 * Composite properties builder
 */
class CompositePropertiesBuilder : CompositeBuilder<Properties>() {

    var map = mutableMapOf<String, Any>()

    /** {@inheritDoc} */
    override fun build(locator: Locator) = when {
        list.isEmpty() -> PropertiesSource.default()
        list.all { it is PropertiesBuilder.NullPropertiesBuilder } ->
            PropertiesSource.nothing()
        else -> {
            val sources = buildListWith(locator, { item ->
                item !is PropertiesBuilder.NullPropertiesBuilder
            })
            if (sources.isEmpty())
                PropertiesSource.nothing()
            else
                CompositeProperties(sources)
        }
    }

    /**
     * add new item to Composite.
     * @param pair The new Property.
     */
    operator fun plusAssign(pair: Pair<String, Any>) {
        if (map.isEmpty())
            plusAssign(MapPropertiesBuilder(map))
        map.put(pair.first, pair.second)
    }
}

class MapPropertiesBuilder(val map: MutableMap<String, Any>) : PropertiesBuilder() {
    override fun build(locator: Locator): Properties? = MapProperties(map)
}

abstract class PredefinedPropertiesBuilder(
        format: String,
        private val factory: PropertiesFactory,
        init: PredefinedPropertiesBuilder.() -> Unit
) : PropertiesBuilder() {

    init {
        this.format = format
        init()
    }

    /** {@inheritDoc} */
    override fun build(locator: Locator): Properties? = factory.make(uri, rootKey).result()
}
