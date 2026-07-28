package io.github.superisland.hook.systemui;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import java.lang.reflect.Modifier;
import org.junit.Test;

public final class XmsfFocusAuthContractTest {
    private static final String PACKAGE_NAME = "com.example.source";
    private static final String KEY = "0|com.example.source|42|null|10123";

    @Test
    public void acceptsOnlyXiaomiFocusScopesWithMatchingCanonicalKey() {
        assertTrue(XmsfFocusAuthContract.isEligibleRequest(20_032L, PACKAGE_NAME, KEY));
        assertTrue(XmsfFocusAuthContract.isEligibleRequest(
                22_624L,
                PACKAGE_NAME,
                "10|com.example.source|-7|tag|with|pipes|1010123"));
        assertTrue(XmsfFocusAuthContract.isEligibleRequest(
                20_032L,
                PACKAGE_NAME,
                "0|com.example.source|42|sync|10123|override|group"));

        assertFalse(XmsfFocusAuthContract.isEligibleRequest(20_031L, PACKAGE_NAME, KEY));
        assertFalse(XmsfFocusAuthContract.isEligibleRequest(20_033L, PACKAGE_NAME, KEY));
        assertFalse(XmsfFocusAuthContract.isEligibleRequest(22_623L, PACKAGE_NAME, KEY));
        assertFalse(XmsfFocusAuthContract.isEligibleRequest(22_625L, PACKAGE_NAME, KEY));

        XmsfFocusAuthContract.FocusRequest request =
                XmsfFocusAuthContract.focusRequestOrNull(20_032L, PACKAGE_NAME, KEY);
        assertEquals(PACKAGE_NAME, request.packageName());
        assertEquals(0, request.userId());
        assertEquals(
                10,
                XmsfFocusAuthContract.focusRequestOrNull(
                        22_624L,
                        PACKAGE_NAME,
                        "10|com.example.source|-7|tag|1010123").userId());
    }

    @Test
    public void rejectsMissingMalformedOrMismatchedPackageAndKey() {
        assertFalse(XmsfFocusAuthContract.isEligibleRequest(20_032L, null, KEY));
        assertFalse(XmsfFocusAuthContract.isEligibleRequest(20_032L, "", KEY));
        assertFalse(XmsfFocusAuthContract.isEligibleRequest(20_032L, "single", KEY));
        assertFalse(XmsfFocusAuthContract.isEligibleRequest(
                20_032L, "com.example bad", KEY));
        assertFalse(XmsfFocusAuthContract.isEligibleRequest(20_032L, PACKAGE_NAME, null));
        assertFalse(XmsfFocusAuthContract.isEligibleRequest(
                20_032L, PACKAGE_NAME, "0|com.example.other|42|null|10123"));
        assertFalse(XmsfFocusAuthContract.isEligibleRequest(
                20_032L, PACKAGE_NAME, "bad-user|com.example.source|42|null|10123"));
        assertFalse(XmsfFocusAuthContract.isEligibleRequest(
                20_032L, PACKAGE_NAME, "0|com.example.source|bad-id|null|10123"));
        assertFalse(XmsfFocusAuthContract.isEligibleRequest(
                20_032L, PACKAGE_NAME, "0|com.example.source|42|null|bad-uid"));
        assertFalse(XmsfFocusAuthContract.isEligibleRequest(
                20_032L, PACKAGE_NAME, "1|com.example.source|42|null|10123"));
        assertFalse(XmsfFocusAuthContract.isEligibleRequest(
                20_032L, PACKAGE_NAME, "0|com.example.source|42|10123"));
    }

    @Test
    public void resolvesTheUniqueObfuscationIndependentStructure() throws Exception {
        XmsfFocusAuthContract.Resolved resolved = XmsfFocusAuthContract.resolve(
                ValidSession.class,
                FakeAuthError.class,
                FakeBundle.class);

        assertEquals("dispatchFailure", resolved.errorMethod().getName());
        assertFalse(Modifier.isStatic(resolved.errorMethod().getModifiers()));
        assertTrue(resolved.isAuthError(new FakeAuthError()));
        assertFalse(resolved.isAuthError(new Object()));
    }

    @Test
    public void rejectsAmbiguousErrorMethod() {
        assertThrows(NoSuchMethodException.class, () -> XmsfFocusAuthContract.resolve(
                AmbiguousErrorSession.class,
                FakeAuthError.class,
                FakeBundle.class));
    }

    @Test
    public void rejectsAmbiguousSuccessMethod() {
        assertThrows(NoSuchMethodException.class, () -> XmsfFocusAuthContract.resolve(
                AmbiguousSuccessSession.class,
                FakeAuthError.class,
                FakeBundle.class));
    }

    @Test
    public void rejectsAmbiguousBundleParameterField() {
        assertThrows(NoSuchFieldException.class, () -> XmsfFocusAuthContract.resolve(
                AmbiguousFieldSession.class,
                FakeAuthError.class,
                FakeBundle.class));
    }

    private static final class FakeBundle {}

    private static final class FakeAuthError {}

    private static class ValidSession {
        private final FakeBundle request = new FakeBundle();

        private FakeBundle dispatchFailure(FakeAuthError ignored) {
            return request;
        }

        private FakeBundle dispatchSuccess() {
            return request;
        }
    }

    private static final class AmbiguousErrorSession extends ValidSession {
        private final FakeBundle request = new FakeBundle();

        private FakeBundle firstFailure(FakeAuthError ignored) {
            return request;
        }

        private FakeBundle secondFailure(FakeAuthError ignored) {
            return request;
        }

        private FakeBundle success() {
            return request;
        }
    }

    private static final class AmbiguousSuccessSession extends ValidSession {
        private final FakeBundle request = new FakeBundle();

        private FakeBundle failure(FakeAuthError ignored) {
            return request;
        }

        private FakeBundle firstSuccess() {
            return request;
        }

        private FakeBundle secondSuccess() {
            return request;
        }
    }

    private static final class AmbiguousFieldSession extends ValidSession {
        private final FakeBundle firstRequest = new FakeBundle();
        private final FakeBundle secondRequest = new FakeBundle();

        private FakeBundle failure(FakeAuthError ignored) {
            return firstRequest;
        }

        private FakeBundle success() {
            return secondRequest;
        }
    }
}
