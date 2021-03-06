package org.maxur.mserv.frame.embedded

import org.jvnet.hk2.annotations.Contract
import org.maxur.mserv.frame.annotation.ServiceName
import org.maxur.mserv.frame.domain.Holder

/**
 * @author myunusov
 * @version 1.0
 * @since <pre>24.06.2017</pre>
 */
@Contract
abstract class EmbeddedServiceFactory {

    @ServiceName
    lateinit var name: String

    abstract fun make(properties: Holder<Any>): EmbeddedService?
}