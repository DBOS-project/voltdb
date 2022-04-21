/* This file is part of VoltDB.
 * Copyright (C) 2008-2022 VoltDB Inc.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with VoltDB.  If not, see <http://www.gnu.org/licenses/>.
 */

package org.voltcore.utils;

import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

import org.voltcore.logging.Level;
import org.voltcore.logging.VoltLogger;

import com.google_voltpatches.common.base.Throwables;
import com.google_voltpatches.common.cache.Cache;
import com.google_voltpatches.common.cache.CacheBuilder;

/*
 * Wraps a VoltLogger to provide rate-limited logging.
 * A single RateLimitedLogger will not log more frequently
 * than its configured limit. This rate limitation is
 * independent of the message and log level.
 */
public class RateLimitedLogger {

    private volatile long m_lastLogTime = 0;

    private final long m_maxLogIntervalMillis;
    private final VoltLogger m_logger;
    private final Level m_level;

    /**
     * Rate-limited logger constructor.
     *
     * @param maxLogIntervalMillis rate limit in millisecs
     * @param logger a {@link VoltLogger}
     * @param level default logging {@link Level}
     */
     public RateLimitedLogger(long maxLogIntervalMillis, VoltLogger logger, Level level) {
        m_maxLogIntervalMillis = maxLogIntervalMillis;
        m_logger = logger;
        m_level = level; // default
    }

    /**
     * Log a rate-limited message using the default level
     * for this logger.
     *
     * @param message string to be logged
     * @param now current time (as millisecs)
     */
    public void log(String message, long now) {
        log(message, now, m_level);
    }

    /**
     * Log a rate-limited message with specified level. Message is
     * formatted by caller if arguments are required, which may add
     * unnecessary overhead if logging is not subsequently needed.
     *
     * @param message string to be logged
     * @param now current time (as millisecs)
     * @param level a logging {@link Level}
     */
    public void log(String message, long now, Level level) {
        if (now - m_lastLogTime > m_maxLogIntervalMillis) {
            synchronized (this) {
                if (now - m_lastLogTime > m_maxLogIntervalMillis) {
                    m_logger.log(level, message, null);
                    m_lastLogTime = now;
                }
            }
        }
    }

    /**
     * Log a rate-limited message. Delays the formatting of the message
     * until (and if) it is actually logged.
     *
     * @param now current time (as millisecs)
     * @param level a logging {@link Level}
     * @param cause evidentiary exception, possibly null
     * @param format a {@link String#format(String, Object...)} string format
     * @param args format arguments
     */
    public void log(long now, Level level, Throwable cause, String format, Object... args) {
        if (now - m_lastLogTime > m_maxLogIntervalMillis) {
            synchronized (this) {
                if (now - m_lastLogTime > m_maxLogIntervalMillis) {
                    if (m_logger.isEnabledFor(level)) {
                        m_logger.logFmt(level, cause, format, args);
                    }
                    m_lastLogTime = now;
                }
            }
        }
    }

    /*
     * Internal cache of loggers used by tryLogForMessage (below)
     */
    private static final Cache<String, RateLimitedLogger> m_loggersCached =
            CacheBuilder.newBuilder().maximumSize(1000).build();

    /**
     * Rate-limited logger. Logger is looked up cache by format
     * prior to expansion with parameters, which is only done
     * if the message is actually logged.
     *
     * @param now current time (as millisecs)
     * @param maxLogInterval suppress time
     * @param maxLogIntervalUnit suppress time units
     * @param logger a {@link VoltLogger}
     * @param level a logging {@link Level}
     * @param format a {@link String#format(String, Object...)} string format
     * @param parameters format arguments
     * @see #tryLogForMessage(long,long,TimeUnit,VoltLogger,Level,Throwable,String,Object...)
     */
    public static void tryLogForMessage(long now,
            final long maxLogInterval,
            final TimeUnit maxLogIntervalUnit,
            final VoltLogger logger,
            final Level level,
            String format, Object... parameters) {
        tryLogForMessage(now, maxLogInterval, maxLogIntervalUnit, logger, level, null, format, parameters);
    }

    /**
     * Rate-limited logger. Logger is looked up cache by format
     * prior to expansion with parameters, which is only done
     * if the message is actually logged.
     * <p>
     * The <code>maxLogInterval</code>, <code>maxLogIntervalUntil</code>,
     * and <code>logger</code> values are used when creating a
     * <code>RateLimitedLogger</code> in the cache, and therefore
     * changing the values on subsequent calls with the same format
     * will not be effective.
     * <p>
     * VoltDB code wishing to use rate-limited logging is recommended
     * to <b>not</b> call this method directly, but to instead use
     * {@link VoltLogger#rateLimitedLog}.
     *
     * @param now current time (as millisecs)
     * @param maxLogInterval suppress time
     * @param maxLogIntervalUnit suppress time units
     * @param logger a {@link VoltLogger}
     * @param level a logging {@link Level}
     * @param cause evidentiary exception (may be null)
     * @param format a {@link String#format(String, Object...)} string format
     * @param parameters format arguments
     */
    public static void tryLogForMessage(long now,
            final long maxLogInterval,
            final TimeUnit maxLogIntervalUnit,
            final VoltLogger logger,
            final Level level, Throwable cause,
            String format, Object... parameters) {

        Callable<RateLimitedLogger> builder = new Callable<RateLimitedLogger>() {
            @Override
            public RateLimitedLogger call() throws Exception {
                return new RateLimitedLogger(maxLogIntervalUnit.toMillis(maxLogInterval), logger, level);
            }
        };

        final RateLimitedLogger rll;
        try {
            rll = m_loggersCached.get(format, builder);
            rll.log(now, level, cause, format, parameters);
        } catch (ExecutionException ex) {
            Throwables.propagate(Throwables.getRootCause(ex));
        }
    }
}
