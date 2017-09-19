package org.broadleafcommerce.core.search.index;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.beans.BeansException;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.core.task.TaskExecutor;
import org.springframework.util.Assert;

import java.util.HashSet;
import java.util.Set;

/**
 * 
 * @author Kelly Tisdell
 *
 * @param <T>
 */
public abstract class AbstractQueueManager<T> implements QueueManager<T>, ApplicationContextAware {
    private static final Log LOG = LogFactory.getLog(AbstractQueueManager.class);
    private static final Set<String> QUEUE_NAMES_IN_USE = new HashSet<>();
    private boolean initialized = false;
    private boolean queueProducerStarted = false;
    protected ApplicationContext ctx;
    
    @Override
    public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
        this.ctx = applicationContext;
    }

    @Override
    public final synchronized void initialize(String processId) {
        if (!initialized) {
            if (QUEUE_NAMES_IN_USE.contains(getQueueName())) {
                throw new IllegalStateException("A QueueManager with the queueName of " + getQueueName() 
                    + " was already in use. Ensure it is stopped first.");
            }
            initializeInternal(processId); //Allow subclasses to do what they need.
            
            //Check all of the components.
            Assert.notNull(getQueueLoader(), "Call to " + getClass().getName() 
                    + ".getQueueLoader() returned null.  It must return an instance of QueueLoader.");
            
            QUEUE_NAMES_IN_USE.add(getQueueName());
            this.initialized = true;
        } else {
            LOG.warn("QueueManager was already started for queue: " + getQueueName());
        }
    }
    
    @Override
    public final synchronized void close(String processId) {
        if (initialized) {
            if (!getQueueLoader().isActive()) {
                closeInternal(processId);
                QUEUE_NAMES_IN_USE.remove(getQueueName());
                initialized = false;
                queueProducerStarted = false;
            } else {
                LOG.warn("QueueManager for queue " + getQueueName() + " cannot be closed because the QueueLoader thread is still "
                        + "running. Use SearchIndexProcessStateHolder.failFast to forcefully stop the process and then call " 
                        + getClass().getName() + ".close() again.");
            }
        }
    }

    @Override
    public final synchronized void startQueueProducer() {
        if (!initialized) {
            throw new IllegalStateException("QueueManager (" + getClass().getName() + ") was not initialized.");
        }
        if (queueProducerStarted) {
            throw new IllegalStateException("QueueLoader (" + getClass().getName() + ") was already started.");
        }
        Thread t = new Thread(getQueueLoader(), getClass().getName() + "-" + getQueueName());
        t.start();
        queueProducerStarted = true;
    }

    @Override
    public final synchronized void startQueueProducer(TaskExecutor t) {
        if (!initialized) {
            throw new IllegalStateException("QueueManager (" + getClass().getName() + ") was not initialized.");
        }
        if (queueProducerStarted) {
            throw new IllegalStateException("QueueLoader (" + getClass().getName() + ") was already started.");
        }
        t.execute(getQueueLoader());
        queueProducerStarted = true;
    }
    
    @Override
    public final synchronized boolean isInitialized() {
        return initialized;
    }
    
    /**
     * Indicates if the QueueProducer has been started in a background thread.
     * @return
     */
    protected final boolean isQueueProducerStarted() {
        return queueProducerStarted;
    }
    
    /**
     * Hook point to allow any additional initialization.  This is called by the initialize method and is guaranteed to 
     * only be invoked once. A RuntimeException should be thrown from this method if there is an issue initializing.
     * @param processId
     */
    protected abstract void initializeInternal(String processId);
    
    /**
     * Hook point to allow any additional cleanup.
     */
    protected abstract void closeInternal(String processId);
    
    
    
}