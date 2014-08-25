/*
 * Copyright 2013, Red Hat, Inc. and individual contributors as indicated by the
 * @author tags. See the copyright.txt file in the distribution for a full
 * listing of individual contributors.
 *
 * This is free software; you can redistribute it and/or modify it under the
 * terms of the GNU Lesser General Public License as published by the Free
 * Software Foundation; either version 2.1 of the License, or (at your option)
 * any later version.
 *
 * This software is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 * FOR A PARTICULAR PURPOSE. See the GNU Lesser General Public License for more
 * details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with this software; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA, or see the FSF
 * site: http://www.fsf.org.
 */
package org.zanata.async;

import java.util.Collection;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.jboss.seam.annotations.In;
import org.junit.Assert;
import org.junit.Test;
import org.zanata.ArquillianTest;

import com.google.common.collect.Lists;

import javax.annotation.Nonnull;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.is;

/**
 * Integration tests for the Asynchrnous task framework.
 *
 * @author Carlos Munoz <a
 *         href="mailto:camunoz@redhat.com">camunoz@redhat.com</a>
 */
public class AsyncTaskITCase /*extends ArquillianTest*/ {
    //@In
    private TaskExecutor taskExecutor = new TaskExecutor();

    private CountDownLatch completed = new CountDownLatch(1);
    private Runnable onComplete = new Runnable() {
        @Override
        public void run() {
            completed.countDown();
        }
    };

    //@Override
    protected void prepareDBUnitOperations() {
    }

    @Test
    public void taskReturnsValue() throws Exception {
        // Given an expected return value
        final String expectedRetVal = "EXPECTED";

        // Start an asynchronous process
        AsyncTaskHandle<String> handle =
                taskExecutor.startTask(new SimpleAsyncTask<String>("taskReturnsValue") {
                    @Override
                    public String call() throws Exception {
                        return expectedRetVal;
                    }
                }, onComplete);

        // Wait for it to finish and get the result
        String comp = handle.get();
        awaitComplete();

        // Must be the same as the component that was inserted outside of the
        // task
        assertThat(comp, equalTo(expectedRetVal));
    }

    private void awaitComplete() throws InterruptedException {
        boolean done = completed.await(10, TimeUnit.SECONDS);
        assertThat(done, is(true));
    }

    @Test
    public void executionError() throws Exception {
        // Start an asynchronous process that throws an exception
        AsyncTaskHandle<String> handle =
                taskExecutor.startTask(new SimpleAsyncTask<String>("executionError") {
                    @Override
                    public String call() throws Exception {
                        throw new RuntimeException("Expected Exception");
                    }
                }, onComplete);

        // Wait for it to finish and get the result
        waitUntilTaskIsDone(handle);
        assertThat(handle.isDone(), is(true));
        awaitComplete();
        try {
            handle.get(); // Should throw an exception
            Assert.fail();
        } catch (ExecutionException e) {
            // expected
        }
    }

    @Test
    public void progressUpdates() throws Exception {
        final List<Integer> progressUpdates = Lists.newArrayList();
        // "Mock" the task handle so that progress updates are recorded
        final AsyncTaskHandle<Void> taskHandle = new AsyncTaskHandle<Void>("progressUpdates") {
            @Override
            public void setCurrentProgress(int progress) {
                super.setCurrentProgress(progress);
                progressUpdates.add(progress);
            }
        };

        // Start an asynchronous process that updates its progress
        AsyncTaskHandle<Void> handle =
                taskExecutor
                        .startTask(new AsyncTask<Void, AsyncTaskHandle<Void>>() {
                            @Nonnull
                            @Override
                            public AsyncTaskHandle<Void> getHandle() {
                                return taskHandle;
                            }

                            @Override
                            public Void call() throws Exception {
                                getHandle().setCurrentProgress(25);
                                getHandle().setCurrentProgress(50);
                                getHandle().setCurrentProgress(75);
                                getHandle().setCurrentProgress(100);
                                return null;
                            }
                        }, onComplete);

        // Wait for it to finish and get the result
        waitUntilTaskIsDone(handle);
        awaitComplete();

        // Progress update calls should match the task's internal updates
        assertThat(handle.getCurrentProgress(), is(100));
        assertThat(progressUpdates.size(), is(4));
        assertThat(progressUpdates, contains(25, 50, 75, 100));
    }

    @Test
    public void multipleTasks() throws Exception {
        final AtomicInteger finshedCount = new AtomicInteger(0);
        // Start multiple asynchronous processes that throw an exception
        Collection<AsyncTaskHandle> allHandles = Lists.newArrayList();
        for(int i = 0; i < 20; i++) {
            AsyncTaskHandle<String> handle =
                taskExecutor.startTask(new SimpleAsyncTask<String>("executionError") {
                    @Override
                    public String call() throws Exception {
                        System.out.println("Running a task...");
                        Thread.sleep(1000);
                        finshedCount.addAndGet(1);
                        return "";
                    }
                }, onComplete);
            allHandles.add(handle);
        }

        // Wait for the last handle to finish and get the result
        waitUntilAllTasksAreDone(allHandles
                .toArray(new AsyncTaskHandle[allHandles.size()]));
        //awaitComplete();
        assertThat(finshedCount.get(), is(20));
    }

    /**
     * This is an active wait for a task to finish. Only use for short lived
     * tasks.
     */
    private static void waitUntilTaskIsDone(AsyncTaskHandle handle) {
        waitUntilAllTasksAreDone(handle);
    }

    private static void waitUntilAllTasksAreDone(AsyncTaskHandle ... handles) {
        for( AsyncTaskHandle h : handles ) {
            while(!h.isDone()) {
                // Active wait
            }
        }
    }

}
