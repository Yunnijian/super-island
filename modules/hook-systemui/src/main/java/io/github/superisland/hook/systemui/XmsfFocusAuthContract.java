package io.github.superisland.hook.systemui;

import android.os.Bundle;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.List;

/** Narrow, fail-closed contract for Xiaomi Focus authorization requests handled by XMSF. */
final class XmsfFocusAuthContract {
    private static final String AUTH_SESSION_CLASS = "com.xiaomi.xms.auth.AuthSession";
    private static final String AUTH_ERROR_CLASS = "com.xiaomi.xms.auth.AuthError";
    private static final String KEY_SCOPE = "scope";
    private static final String KEY_PACKAGE_NAME = "package_name";
    private static final String KEY_NOTIFICATION = "notification_key";
    private static final long RELEASE_FOCUS_SCOPE = 20_032L;
    private static final long TEST_FOCUS_SCOPE = 22_624L;
    private static final int MAX_PACKAGE_LENGTH = 255;
    private static final int MAX_NOTIFICATION_KEY_LENGTH = 4_096;

    private XmsfFocusAuthContract() {}

    static Resolved resolve(ClassLoader classLoader) throws ReflectiveOperationException {
        Class<?> authSession = Class.forName(AUTH_SESSION_CLASS, false, classLoader);
        Class<?> authError = Class.forName(AUTH_ERROR_CLASS, false, classLoader);
        return resolve(authSession, authError, Bundle.class);
    }

    static Resolved resolve(Class<?> authSession, Class<?> authError, Class<?> bundleClass)
            throws ReflectiveOperationException {
        List<Method> errorMethods = new ArrayList<>();
        List<Method> successMethods = new ArrayList<>();
        for (Method method : authSession.getDeclaredMethods()) {
            if (Modifier.isStatic(method.getModifiers())
                    || method.getReturnType() != bundleClass) {
                continue;
            }
            Class<?>[] parameterTypes = method.getParameterTypes();
            if (parameterTypes.length == 1 && parameterTypes[0] == authError) {
                errorMethods.add(method);
            } else if (parameterTypes.length == 0) {
                successMethods.add(method);
            }
        }
        if (errorMethods.size() != 1) {
            throw new NoSuchMethodException(
                    "Expected exactly one AuthError -> Bundle method, found "
                            + errorMethods.size());
        }
        if (successMethods.size() != 1) {
            throw new NoSuchMethodException(
                    "Expected exactly one () -> Bundle method, found "
                            + successMethods.size());
        }

        List<Field> parameterFields = new ArrayList<>();
        for (Field field : authSession.getDeclaredFields()) {
            if (!Modifier.isStatic(field.getModifiers()) && field.getType() == bundleClass) {
                parameterFields.add(field);
            }
        }
        if (parameterFields.size() != 1) {
            throw new NoSuchFieldException(
                    "Expected exactly one AuthSession Bundle field, found "
                            + parameterFields.size());
        }

        Method errorMethod = errorMethods.get(0);
        Method successMethod = successMethods.get(0);
        Field parameterField = parameterFields.get(0);
        errorMethod.setAccessible(true);
        successMethod.setAccessible(true);
        parameterField.setAccessible(true);
        return new Resolved(authError, errorMethod, successMethod, parameterField);
    }

    static boolean isEligibleRequest(long scope, String packageName, String notificationKey) {
        return focusRequestOrNull(scope, packageName, notificationKey) != null;
    }

    static FocusRequest focusRequestOrNull(
            long scope,
            String packageName,
            String notificationKey) {
        if (!isFocusScope(scope) || !isValidPackageName(packageName)) return null;
        int userId = notificationKeyUserIdOrInvalid(notificationKey, packageName);
        return userId >= 0 ? new FocusRequest(packageName, userId) : null;
    }

    private static boolean isFocusScope(long scope) {
        return scope == RELEASE_FOCUS_SCOPE || scope == TEST_FOCUS_SCOPE;
    }

    private static boolean isValidPackageName(String packageName) {
        if (packageName == null
                || packageName.length() == 0
                || packageName.length() > MAX_PACKAGE_LENGTH) {
            return false;
        }
        int segmentStart = 0;
        int segmentCount = 0;
        for (int index = 0; index <= packageName.length(); index++) {
            if (index != packageName.length() && packageName.charAt(index) != '.') continue;
            if (index == segmentStart || !isAsciiLetter(packageName.charAt(segmentStart))) {
                return false;
            }
            for (int character = segmentStart + 1; character < index; character++) {
                char value = packageName.charAt(character);
                if (!isAsciiLetter(value) && !isAsciiDigit(value) && value != '_') {
                    return false;
                }
            }
            segmentCount++;
            segmentStart = index + 1;
        }
        return segmentCount >= 2;
    }

    private static int notificationKeyUserIdOrInvalid(
            String notificationKey,
            String packageName) {
        if (notificationKey == null
                || notificationKey.length() == 0
                || notificationKey.length() > MAX_NOTIFICATION_KEY_LENGTH) {
            return -1;
        }
        int userSeparator = notificationKey.indexOf('|');
        int packageSeparator = notificationKey.indexOf('|', userSeparator + 1);
        int idSeparator = notificationKey.indexOf('|', packageSeparator + 1);
        int tagSeparator = notificationKey.indexOf('|', idSeparator + 1);
        if (userSeparator <= 0
                || packageSeparator <= userSeparator + 1
                || idSeparator <= packageSeparator + 1
                || tagSeparator < 0) {
            return -1;
        }
        if (!packageName.equals(notificationKey.substring(userSeparator + 1, packageSeparator))) {
            return -1;
        }
        int userId = parseNonNegativeInt(notificationKey, 0, userSeparator);
        return userId >= 0
                        && parsesInt(notificationKey, packageSeparator + 1, idSeparator)
                        && containsUidForUser(notificationKey, tagSeparator + 1, userId)
                ? userId
                : -1;
    }

    private static boolean containsUidForUser(String value, int start, int userId) {
        int segmentStart = start;
        while (segmentStart < value.length()) {
            int separator = value.indexOf('|', segmentStart);
            int segmentEnd = separator >= 0 ? separator : value.length();
            int candidate = parseNonNegativeInt(value, segmentStart, segmentEnd);
            if (candidate >= 0 && candidate / 100_000 == userId) return true;
            if (separator < 0) return false;
            segmentStart = separator + 1;
        }
        return false;
    }

    private static int parseNonNegativeInt(String value, int start, int end) {
        if (start >= end) return -1;
        for (int index = start; index < end; index++) {
            if (!isAsciiDigit(value.charAt(index))) return -1;
        }
        try {
            int parsed = Integer.parseInt(value.substring(start, end));
            return parsed >= 0 ? parsed : -1;
        } catch (NumberFormatException ignored) {
            return -1;
        }
    }

    private static boolean parsesInt(String value, int start, int end) {
        if (start >= end) return false;
        int digitsStart = value.charAt(start) == '-' ? start + 1 : start;
        if (digitsStart >= end) return false;
        for (int index = digitsStart; index < end; index++) {
            if (!isAsciiDigit(value.charAt(index))) return false;
        }
        try {
            Integer.parseInt(value.substring(start, end));
            return true;
        } catch (NumberFormatException ignored) {
            return false;
        }
    }

    private static boolean isAsciiLetter(char value) {
        return (value >= 'a' && value <= 'z') || (value >= 'A' && value <= 'Z');
    }

    private static boolean isAsciiDigit(char value) {
        return value >= '0' && value <= '9';
    }

    record FocusRequest(String packageName, int userId) {}

    static final class Resolved {
        private final Class<?> authErrorClass;
        private final Method errorMethod;
        private final Method successMethod;
        private final Field parameterField;

        Resolved(
                Class<?> authErrorClass,
                Method errorMethod,
                Method successMethod,
                Field parameterField) {
            this.authErrorClass = authErrorClass;
            this.errorMethod = errorMethod;
            this.successMethod = successMethod;
            this.parameterField = parameterField;
        }

        Method errorMethod() {
            return errorMethod;
        }

        boolean isAuthError(Object value) {
            return authErrorClass.isInstance(value);
        }

        FocusRequest focusRequestOrNull(Object authSession) throws IllegalAccessException {
            if (authSession == null) return null;
            Object value = parameterField.get(authSession);
            if (!(value instanceof Bundle parameters)) return null;
            Object scope = parameters.get(KEY_SCOPE);
            Object packageName = parameters.get(KEY_PACKAGE_NAME);
            Object notificationKey = parameters.get(KEY_NOTIFICATION);
            return scope instanceof Long
                    && packageName instanceof String
                    && notificationKey instanceof String
                    ? XmsfFocusAuthContract.focusRequestOrNull(
                            (Long) scope,
                            (String) packageName,
                            (String) notificationKey)
                    : null;
        }

        boolean isEligibleFocusRequest(Object authSession) throws IllegalAccessException {
            return focusRequestOrNull(authSession) != null;
        }

        Object invokeSuccess(Object authSession) throws ReflectiveOperationException {
            return successMethod.invoke(authSession);
        }
    }
}
