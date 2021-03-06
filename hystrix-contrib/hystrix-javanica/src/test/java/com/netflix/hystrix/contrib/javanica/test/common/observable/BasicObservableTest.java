package com.netflix.hystrix.contrib.javanica.test.common.observable;

import com.netflix.hystrix.HystrixEventType;
import com.netflix.hystrix.HystrixRequestLog;
import com.netflix.hystrix.contrib.javanica.annotation.HystrixCommand;
import com.netflix.hystrix.contrib.javanica.command.ObservableResult;
import com.netflix.hystrix.contrib.javanica.test.common.BasicHystrixTest;
import com.netflix.hystrix.contrib.javanica.test.common.domain.User;
import org.apache.commons.lang3.Validate;
import org.junit.Before;
import org.junit.Test;
import rx.Observable;
import rx.Observer;
import rx.functions.Action1;

import static com.netflix.hystrix.contrib.javanica.test.common.CommonUtils.getHystrixCommandByKey;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * Created by dmgcodevil
 */
public abstract class BasicObservableTest extends BasicHystrixTest {


    private UserService userService;

    protected abstract UserService createUserService();

    @Before
    public void setUp() throws Exception {
        super.setUp();
        userService = createUserService();
    }

    @Test
    public void testGetUserByIdObservable() {
        // blocking
        assertEquals("name: 1", userService.getUser("1", "name: ").toBlocking().single().getName());

        // non-blocking
        // - this is a verbose anonymous inner-class approach and doesn't do assertions
        Observable<User> fUser = userService.getUser("1", "name: ");
        fUser.subscribe(new Observer<User>() {

            @Override
            public void onCompleted() {
                // nothing needed here
            }

            @Override
            public void onError(Throwable e) {
                e.printStackTrace();
            }

            @Override
            public void onNext(User v) {
                System.out.println("onNext: " + v);
            }

        });

        Observable<User> fs = userService.getUser("1", "name: ");
        fs.subscribe(new Action1<User>() {

            @Override
            public void call(User user) {
                assertEquals("name: 1", user.getName());
            }
        });
        assertEquals(3, HystrixRequestLog.getCurrentRequest().getAllExecutedCommands().size());
        com.netflix.hystrix.HystrixInvokableInfo getUserCommand = getHystrixCommandByKey("getUser");
        assertTrue(getUserCommand.getExecutionEvents().contains(HystrixEventType.SUCCESS));
    }

    @Test
    public void testGetUserWithFallback() {
        final User exUser = new User("def", "def");

        // blocking
        assertEquals(exUser, userService.getUser(" ", "").toBlocking().single());
        assertEquals(1, HystrixRequestLog.getCurrentRequest().getAllExecutedCommands().size());
        com.netflix.hystrix.HystrixInvokableInfo getUserCommand = getHystrixCommandByKey("getUser");
        // confirm that command has failed
        assertTrue(getUserCommand.getExecutionEvents().contains(HystrixEventType.FAILURE));
        // and that fallback was successful
        assertTrue(getUserCommand.getExecutionEvents().contains(HystrixEventType.FALLBACK_SUCCESS));
    }

    public static class UserService {

        @HystrixCommand(fallbackMethod = "staticFallback")
        public Observable<User> getUser(final String id, final String name) {
            return new ObservableResult<User>() {
                @Override
                public User invoke() {
                    validate(id, name);
                    return new User(id, name + id);
                }
            };
        }

        private User staticFallback(String id, String name) {
            return new User("def", "def");
        }

        private void validate(String id, String name) {
            Validate.notBlank(id);
            Validate.notBlank(name);
        }
    }
}
