package com.sirma.itt.seip.security.context;

import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.fail;
import static org.mockito.Mockito.when;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertTrue;

import java.util.Collections;

import javax.enterprise.inject.Instance;

import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.testng.Assert;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import com.sirma.itt.seip.security.User;
import com.sirma.itt.seip.security.authentication.AuthenticationContext;
import com.sirma.itt.seip.security.authentication.Authenticator;
import com.sirma.itt.seip.security.configuration.SecurityConfiguration;
import com.sirma.itt.seip.security.exception.AccountDisabledException;
import com.sirma.itt.seip.security.exception.ContextNotActiveException;
import com.sirma.itt.seip.security.exception.SecurityException;
import com.sirma.itt.seip.security.mocks.ConfigurationPropertyMock;
import com.sirma.itt.seip.security.mocks.UserMock;

/**
 * The Class DefaultSecurityContextManagerTest.
 *
 * @author BBonev
 */
@Test(singleThreaded = true)
public class DefaultSecurityContextManagerTest {

	private static final String TEST_TENANT_ID = "tenant";

	@Mock
	Authenticator authenticator;

	@Mock
	SecurityConfiguration securityConfiguration;

	@Mock
	Instance<SecurityConfiguration> securityConfigurationInstance;

	@Mock
	private AdminResolver adminResolverMock;

	@Mock
	Instance<AdminResolver> adminResolver;

	@InjectMocks
	DefaultSecurityContextManager contextManager;

	@BeforeMethod
	public void beforeMethod() {
		SecurityContextHolder.clear();
		MockitoAnnotations.initMocks(this);
		when(securityConfigurationInstance.get()).thenReturn(securityConfiguration);
		when(adminResolver.get()).thenReturn(adminResolverMock);
	}

	public void test_initializeExecution_authentication() {
		AuthenticationContext authenticationContext = AuthenticationContext
				.create(Collections.singletonMap("name", "test"));
		when(authenticator.authenticate(authenticationContext)).thenReturn(new UserMock("test", TEST_TENANT_ID));

		contextManager.initializeExecution(authenticationContext);
		SecurityContext context = contextManager.getCurrentContext();
		assertTrue(context.isActive());
		assertTrue(context.isAuthenticated());
		assertEquals(context.getAuthenticated().getIdentityId(), "test");
		assertEquals(context.getEffectiveAuthentication().getIdentityId(), "test");
		assertEquals(context.getCurrentTenantId(), TEST_TENANT_ID);
	}

	public void test_initializeExecution_notAuthenticated() {
		AuthenticationContext authenticationContext = AuthenticationContext
				.create(Collections.singletonMap("name", "test"));
		when(authenticator.authenticate(authenticationContext)).thenReturn(null);

		Assert.assertFalse(contextManager.initializeExecution(authenticationContext));
	}

	@Test
	public void test_initializeExecution_AlreadyActive_sameUser() {
		AuthenticationContext authenticationContext = AuthenticationContext
				.create(Collections.singletonMap("name", "test"));
		when(authenticator.authenticate(authenticationContext)).thenReturn(new UserMock("test", TEST_TENANT_ID));

		contextManager.initializeExecution(authenticationContext);

		contextManager.initializeExecution(authenticationContext);
	}

	@Test(expectedExceptions = SecurityException.class)
	public void test_initializeExecution_AlreadyActive_differentUser() {
		AuthenticationContext authenticationContext1 = AuthenticationContext
				.create(Collections.singletonMap("name", "test"));
		when(authenticator.authenticate(authenticationContext1)).thenReturn(new UserMock("test", TEST_TENANT_ID));

		contextManager.initializeExecution(authenticationContext1);

		AuthenticationContext authenticationContext2 = AuthenticationContext
				.create(Collections.singletonMap("name", "test2"));
		when(authenticator.authenticate(authenticationContext2)).thenReturn(new UserMock("test2", TEST_TENANT_ID));

		contextManager.initializeExecution(authenticationContext2);
	}

	@Test(expectedExceptions = AccountDisabledException.class)
	public void test_initializeExecution_ShouldNotAllowLoginToUsersThatAreNotAllowedToLogin() {
		AuthenticationContext authenticationContext = AuthenticationContext
				.create(Collections.singletonMap("name", "test"));
		when(authenticator.authenticate(authenticationContext)).thenReturn(new UserMock("test", TEST_TENANT_ID).setCanLogin(false));

		contextManager.initializeExecution(authenticationContext);
	}

	@Test
	public void test_initializeExecutionAsSystem() {

		setUpSecurityConfiguration();

		contextManager.initializeExecutionAsSystemAdmin();

		SecurityContext context = contextManager.getCurrentContext();
		assertTrue(context.isActive());
		assertTrue(context.isAuthenticated());
		assertEquals(context.getAuthenticated().getIdentityId(), "systemadmin");
		assertEquals(context.getEffectiveAuthentication().getIdentityId(), "systemadmin");
		assertEquals(context.getCurrentTenantId(), SecurityContext.SYSTEM_TENANT);
	}

	@Test(expectedExceptions = SecurityException.class)
	public void test_beginContextExecution_RequestId_invalidId_null() {
		contextManager.beginContextExecution((String) null);
	}

	@Test(expectedExceptions = SecurityException.class)
	public void test_beginContextExecution_RequestId_invalidId_empty() {
		contextManager.beginContextExecution("");
	}

	@Test(expectedExceptions = ContextNotActiveException.class)
	public void test_beginContextExecution_RequestId_ContextNotActive() {
		contextManager.beginContextExecution("new-request-id");
	}

	@Test
	public void test_beginContextExecution_byRequestId() {
		when(securityConfiguration.getSystemAdminUser())
				.thenReturn(new UserMock("systemadmin", SecurityContext.SYSTEM_TENANT));

		contextManager.initializeExecutionAsSystemAdmin();
		SecurityContext context = contextManager.getCurrentContext();
		assertNotEquals(context.getRequestId(), "new-request-id");
		contextManager.beginContextExecution("new-request-id");
		context = contextManager.getCurrentContext();
		assertEquals(context.getRequestId(), "new-request-id");
	}

	@Test
	public void test_initializeTenantContext() {
		setUpSecurityConfiguration();

		contextManager.initializeTenantContext(TEST_TENANT_ID);

		SecurityContext context = contextManager.getCurrentContext();
		assertTrue(context.isActive());
		assertTrue(context.isAuthenticated());
		assertEquals(context.getAuthenticated().getIdentityId(), "system");
		assertEquals(context.getEffectiveAuthentication().getIdentityId(), "admin");
		assertEquals(context.getEffectiveAuthentication().getTenantId(), TEST_TENANT_ID);
		assertEquals(context.getCurrentTenantId(), TEST_TENANT_ID);
	}

	@Test
	public void test_initializeTenantContext_alreadyActive() {
		setUpSecurityConfiguration();

		contextManager.initializeTenantContext(TEST_TENANT_ID);

		SecurityContext context = contextManager.getCurrentContext();
		assertTrue(context.isActive());
		assertTrue(context.isAuthenticated());
		assertEquals(context.getAuthenticated().getIdentityId(), "system");
		assertEquals(context.getEffectiveAuthentication().getIdentityId(), "admin");
		assertEquals(context.getEffectiveAuthentication().getTenantId(), TEST_TENANT_ID);
		assertEquals(context.getCurrentTenantId(), TEST_TENANT_ID);

		contextManager.initializeTenantContext("tenant2");

		assertTrue(context.isActive());
		assertTrue(context.isAuthenticated());
		assertEquals(context.getAuthenticated().getIdentityId(), "system");
		assertEquals(context.getEffectiveAuthentication().getIdentityId(), "admin");
		assertEquals(context.getEffectiveAuthentication().getTenantId(), TEST_TENANT_ID);
		assertEquals(context.getCurrentTenantId(), TEST_TENANT_ID, "should be the same tenant");
	}

	@Test
	public void test_initializeFromContext() {
		setUpSecurityConfiguration();

		contextManager.initializeTenantContext(TEST_TENANT_ID);
		SecurityContext securityContext = contextManager.createTransferableContext();
		assertTrue(securityContext.isActive());
		assertTrue(securityContext.isAuthenticated());
		assertEquals(securityContext.getAuthenticated().getIdentityId(), "system");
		assertEquals(securityContext.getEffectiveAuthentication().getIdentityId(), "admin");
		assertEquals(securityContext.getCurrentTenantId(), TEST_TENANT_ID);

		SecurityContextHolder.clear();
		SecurityContext context = contextManager.getCurrentContext();

		assertFalse(context.isActive(), "The context should not be active here");

		contextManager.initializeFromContext(securityContext);

		assertTrue(context.isActive());
		assertTrue(context.isAuthenticated());
		assertEquals(context.getAuthenticated().getIdentityId(), "system");
		assertEquals(context.getEffectiveAuthentication().getIdentityId(), "admin");
		assertEquals(context.getEffectiveAuthentication().getTenantId(), TEST_TENANT_ID);
		assertEquals(context.getCurrentTenantId(), TEST_TENANT_ID);
	}

	@Test(expectedExceptions = ContextNotActiveException.class)
	public void test_endExecution_notActive() {
		contextManager.endExecution();
	}

	@Test
	public void test_endExecution() {
		setUpSecurityConfiguration();

		contextManager.initializeTenantContext(TEST_TENANT_ID);

		SecurityContext context = contextManager.getCurrentContext();

		assertTrue(context.isActive());

		contextManager.endExecution();

		assertFalse(context.isActive());
	}

	@Test
	public void test_endExecution_multiple() {
		setUpSecurityConfiguration();

		contextManager.initializeTenantContext(TEST_TENANT_ID);
		contextManager.initializeTenantContext(TEST_TENANT_ID);
		contextManager.initializeTenantContext(TEST_TENANT_ID);

		SecurityContext context = contextManager.getCurrentContext();

		assertTrue(context.isActive());

		contextManager.endExecution();

		assertFalse(context.isActive());
	}

	@Test
	public void test_endContextExecution() {

		setUpSecurityConfiguration();

		contextManager.initializeExecutionAsSystemAdmin();

		contextManager.initializeTenantContext(TEST_TENANT_ID);

		SecurityContext context = contextManager.getCurrentContext();

		assertTrue(context.isActive());
		assertEquals(context.getCurrentTenantId(), TEST_TENANT_ID);

		contextManager.endContextExecution();

		assertTrue(context.isActive());
		assertEquals(context.getCurrentTenantId(), SecurityContext.SYSTEM_TENANT);

		contextManager.endContextExecution();

		assertFalse(context.isActive());
	}

	private void setUpSecurityConfiguration() {
		when(securityConfiguration.getSystemAdminUser())
				.thenReturn(new UserMock("systemadmin", SecurityContext.SYSTEM_TENANT));

		when(securityConfiguration.getSystemUser())
				.thenReturn(new ConfigurationPropertyMock<User>(new UserMock("system", TEST_TENANT_ID)));
		when(securityConfiguration.getAdminUser())
				.thenReturn(new ConfigurationPropertyMock<User>(new UserMock("admin", TEST_TENANT_ID)));
	}

	@Test(expectedExceptions = SecurityException.class)
	public void test_transferrableContextValidation_invalid() {

		SecurityContext context = contextManager.createTemporaryContext(new UserMock("admin", TEST_TENANT_ID));

		assertTrue(context.isActive());
		assertEquals(context.getCurrentTenantId(), TEST_TENANT_ID);

		contextManager.initializeFromContext(context);
	}

	@Test(expectedExceptions = ContextNotActiveException.class)
	public void test_beginContextExecution_noContext() {
		contextManager.beginContextExecution(new UserMock("", ""));
	}

	@Test
	public void test_beginContextExecution_shouldOverrideEffectiveAuthenticationOnly() {
		setUpSecurityConfiguration();
		contextManager.initializeExecutionAsSystemAdmin();
		contextManager.initializeTenantContext(TEST_TENANT_ID);
		String identityId = "user@" + TEST_TENANT_ID;
		contextManager.beginContextExecution(new UserMock(identityId, TEST_TENANT_ID));
		assertEquals(contextManager.getCurrentContext().getAuthenticated().getIdentityId(), "system");
		assertEquals(contextManager.getCurrentContext().getEffectiveAuthentication().getIdentityId(), identityId);
	}

	@Test(expectedExceptions = ContextNotActiveException.class)
	public void test_beginContextExecutionAs_noContext() {
		contextManager.beginContextExecutionAs(new UserMock("", ""));
	}

	@Test
	public void test_beginContextExecutionAs_shouldOverrideAllAuthentications() {
		setUpSecurityConfiguration();
		contextManager.initializeExecutionAsSystemAdmin();
		contextManager.initializeTenantContext(TEST_TENANT_ID);
		String identityId = "user@" + TEST_TENANT_ID;
		contextManager.beginContextExecutionAs(new UserMock(identityId, TEST_TENANT_ID));
		assertEquals(contextManager.getCurrentContext().getAuthenticated().getIdentityId(), identityId);
		assertEquals(contextManager.getCurrentContext().getEffectiveAuthentication().getIdentityId(), identityId);
	}

	@Test(expectedExceptions = SecurityException.class)
	public void test_beginContextExecutionAs_shouldFailIfNotSystemOrAdminUser() {
		setUpSecurityConfiguration();
		contextManager.initializeExecutionAsSystemAdmin();
		contextManager.initializeTenantContext(TEST_TENANT_ID);
		contextManager.beginContextExecutionAs(new UserMock("nonAdminUser@" + TEST_TENANT_ID, TEST_TENANT_ID));
		contextManager.beginContextExecutionAs(new UserMock("user2@" + TEST_TENANT_ID, TEST_TENANT_ID));
	}

	@Test
	public void test_beginContextExecutionAs_shouldNotFailIfSameUserPassedTwice() {
		setUpSecurityConfiguration();
		contextManager.initializeExecutionAsSystemAdmin();
		contextManager.initializeTenantContext(TEST_TENANT_ID);
		contextManager.beginContextExecutionAs(new UserMock("nonAdminUser@" + TEST_TENANT_ID, TEST_TENANT_ID));
		contextManager.beginContextExecutionAs(new UserMock("nonAdminUser@" + TEST_TENANT_ID, TEST_TENANT_ID));
	}

	@Test(expectedExceptions = SecurityException.class)
	public void test_beginContextExecutionAs_shouldFailIfNotSameTenant() {
		setUpSecurityConfiguration();
		contextManager.initializeExecutionAsSystemAdmin();
		contextManager.initializeTenantContext(TEST_TENANT_ID);
		contextManager.beginContextExecutionAs(new UserMock("user@test.com", "test.com"));
	}

	@Test
	public void test_transferrableContextValidation_valid() {
		when(securityConfiguration.getSystemAdminUser())
				.thenReturn(new UserMock("systemadmin", SecurityContext.SYSTEM_TENANT));

		when(securityConfiguration.getSystemUser()).thenReturn(
				new ConfigurationPropertyMock<User>(new UserMock("systemadmin", SecurityContext.SYSTEM_TENANT)));
		when(securityConfiguration.getAdminUser())
				.thenReturn(new ConfigurationPropertyMock<User>(new UserMock("admin", SecurityContext.SYSTEM_TENANT)));

		contextManager.initializeExecutionAsSystemAdmin();

		SecurityContext context = contextManager.createTransferableContext();

		assertTrue(context.isActive());
		assertEquals(context.getCurrentTenantId(), SecurityContext.SYSTEM_TENANT);

		SecurityContextHolder.clear();

		assertTrue(context.isActive(), "Context should still be active");
		assertFalse(contextManager.getCurrentContext().isActive());

		contextManager.initializeFromContext(context);

		assertTrue(context.isActive());
		assertEquals(context.getCurrentTenantId(), SecurityContext.SYSTEM_TENANT);
	}

	@Test
	public void test_executeInContext_user_callable() throws Exception {

		when(securityConfiguration.getSystemAdminUser())
				.thenReturn(new UserMock("systemadmin", SecurityContext.SYSTEM_TENANT));

		when(securityConfiguration.getSystemUser()).thenReturn(
				new ConfigurationPropertyMock<User>(new UserMock("systemadmin", SecurityContext.SYSTEM_TENANT)));
		when(securityConfiguration.getAdminUser())
				.thenReturn(new ConfigurationPropertyMock<User>(new UserMock("admin", SecurityContext.SYSTEM_TENANT)));

		contextManager.initializeExecutionAsSystemAdmin();

		String result = contextManager.executeWithPermissionsOf(new UserMock("test", TEST_TENANT_ID)).callable(() -> {
			assertEquals(contextManager.getCurrentContext().getCurrentTenantId(), SecurityContext.SYSTEM_TENANT);
			return "test";
		});
		assertEquals(result, "test");
	}

	@Test
	public void test_executeAsTenant_executable() {
		when(securityConfiguration.getSystemAdminUser())
				.thenReturn(new UserMock("systemadmin", SecurityContext.SYSTEM_TENANT));

		when(securityConfiguration.getSystemUser()).thenReturn(
				new ConfigurationPropertyMock<User>(new UserMock("systemadmin", SecurityContext.SYSTEM_TENANT)));
		when(securityConfiguration.getAdminUser())
				.thenReturn(new ConfigurationPropertyMock<User>(new UserMock("admin", SecurityContext.SYSTEM_TENANT)));

		contextManager.initializeExecutionAsSystemAdmin();

		contextManager.executeAsTenant(TEST_TENANT_ID).executable(() -> {
			assertEquals(contextManager.getCurrentContext().getCurrentTenantId(), TEST_TENANT_ID);
		});
		assertEquals(contextManager.getCurrentContext().getCurrentTenantId(), SecurityContext.SYSTEM_TENANT);
	}

	@Test
	public void test_executeAsTenant_supplier() {
		when(securityConfiguration.getSystemAdminUser())
				.thenReturn(new UserMock("systemadmin", SecurityContext.SYSTEM_TENANT));
		when(securityConfiguration.getSystemUser()).thenReturn(
				new ConfigurationPropertyMock<User>(new UserMock("systemadmin", SecurityContext.SYSTEM_TENANT)));
		when(securityConfiguration.getAdminUser())
				.thenReturn(new ConfigurationPropertyMock<User>(new UserMock("admin", SecurityContext.SYSTEM_TENANT)));

		contextManager.initializeExecutionAsSystemAdmin();

		String result = contextManager.executeAsTenant(TEST_TENANT_ID).supplier(() -> {
			assertEquals(contextManager.getCurrentContext().getCurrentTenantId(), TEST_TENANT_ID);
			return "test";
		});
		assertEquals(result, "test");
		assertEquals(contextManager.getCurrentContext().getCurrentTenantId(), SecurityContext.SYSTEM_TENANT);
	}

	@Test
	public void test_executeAsTenant_function() {

		when(securityConfiguration.getSystemAdminUser())
				.thenReturn(new UserMock("systemadmin", SecurityContext.SYSTEM_TENANT));

		when(securityConfiguration.getSystemUser()).thenReturn(
				new ConfigurationPropertyMock<User>(new UserMock("systemadmin", SecurityContext.SYSTEM_TENANT)));
		when(securityConfiguration.getAdminUser())
				.thenReturn(new ConfigurationPropertyMock<User>(new UserMock("admin", SecurityContext.SYSTEM_TENANT)));

		contextManager.initializeExecutionAsSystemAdmin();

		String result = contextManager.executeAsTenant(TEST_TENANT_ID).function(c -> {
			assertEquals(contextManager.getCurrentContext().getCurrentTenantId(), TEST_TENANT_ID);
			return c;
		}, "arg");
		assertEquals(result, "arg");
		assertEquals(contextManager.getCurrentContext().getCurrentTenantId(), SecurityContext.SYSTEM_TENANT);
	}

	@Test
	public void test_executeAsSystem_executable() {

		AuthenticationContext authenticationContext = AuthenticationContext
				.create(Collections.singletonMap("name", "test"));
		when(authenticator.authenticate(authenticationContext)).thenReturn(new UserMock("test", TEST_TENANT_ID));

		contextManager.initializeExecution(authenticationContext);

		when(securityConfiguration.getSystemUser())
				.thenReturn(new ConfigurationPropertyMock<User>(new UserMock("system", TEST_TENANT_ID)));
		when(securityConfiguration.getAdminUser())
				.thenReturn(new ConfigurationPropertyMock<User>(new UserMock("admin", TEST_TENANT_ID)));

		contextManager.executeAsSystem().executable(() -> {
			assertEquals(contextManager.getCurrentContext().getCurrentTenantId(), TEST_TENANT_ID);
			assertEquals(contextManager.getCurrentContext().getAuthenticated().getIdentityId(), "system");
			assertEquals(contextManager.getCurrentContext().getEffectiveAuthentication().getIdentityId(), "admin");
		});
		assertEquals(contextManager.getCurrentContext().getEffectiveAuthentication().getIdentityId(), "test");
		assertEquals(contextManager.getCurrentContext().getCurrentTenantId(), TEST_TENANT_ID);
	}

	@Test
	public void test_executeAsSystem_callable() throws Exception {

		AuthenticationContext authenticationContext = AuthenticationContext
				.create(Collections.singletonMap("name", "test"));
		when(authenticator.authenticate(authenticationContext)).thenReturn(new UserMock("test", TEST_TENANT_ID));

		contextManager.initializeExecution(authenticationContext);

		when(securityConfiguration.getSystemUser())
				.thenReturn(new ConfigurationPropertyMock<User>(new UserMock("system", TEST_TENANT_ID)));
		when(securityConfiguration.getAdminUser())
				.thenReturn(new ConfigurationPropertyMock<User>(new UserMock("admin", TEST_TENANT_ID)));

		String result = contextManager.executeAsSystem().supplier(() -> {
			assertEquals(contextManager.getCurrentContext().getCurrentTenantId(), TEST_TENANT_ID);
			assertEquals(contextManager.getCurrentContext().getAuthenticated().getIdentityId(), "system");
			assertEquals(contextManager.getCurrentContext().getEffectiveAuthentication().getIdentityId(), "admin");
			return "test";
		});
		assertEquals(result, "test");
		assertEquals(contextManager.getCurrentContext().getEffectiveAuthentication().getIdentityId(), "test");
		assertEquals(contextManager.getCurrentContext().getCurrentTenantId(), TEST_TENANT_ID);
	}

	@Test
	public void test_executeAsSystem_function() throws Exception {

		AuthenticationContext authenticationContext = AuthenticationContext
				.create(Collections.singletonMap("name", "test"));
		when(authenticator.authenticate(authenticationContext)).thenReturn(new UserMock("test", TEST_TENANT_ID));

		contextManager.initializeExecution(authenticationContext);

		when(securityConfiguration.getSystemUser())
				.thenReturn(new ConfigurationPropertyMock<User>(new UserMock("system", TEST_TENANT_ID)));
		when(securityConfiguration.getAdminUser())
				.thenReturn(new ConfigurationPropertyMock<User>(new UserMock("admin", TEST_TENANT_ID)));

		String result = contextManager.executeAsSystem().function((arg) -> {
			assertEquals(contextManager.getCurrentContext().getCurrentTenantId(), TEST_TENANT_ID);
			assertEquals(contextManager.getCurrentContext().getAuthenticated().getIdentityId(), "system");
			assertEquals(contextManager.getCurrentContext().getEffectiveAuthentication().getIdentityId(), "admin");
			return arg;
		}, "test");
		assertEquals(result, "test");
		assertEquals(contextManager.getCurrentContext().getEffectiveAuthentication().getIdentityId(), "test");
		assertEquals(contextManager.getCurrentContext().getCurrentTenantId(), TEST_TENANT_ID);
	}

	@Test
	public void test_executeAsSystem_consumer() throws Exception {

		AuthenticationContext authenticationContext = AuthenticationContext
				.create(Collections.singletonMap("name", "test"));
		when(authenticator.authenticate(authenticationContext)).thenReturn(new UserMock("test", TEST_TENANT_ID));

		contextManager.initializeExecution(authenticationContext);

		when(securityConfiguration.getSystemUser())
				.thenReturn(new ConfigurationPropertyMock<User>(new UserMock("system", TEST_TENANT_ID)));
		when(securityConfiguration.getAdminUser())
				.thenReturn(new ConfigurationPropertyMock<User>(new UserMock("admin", TEST_TENANT_ID)));

		contextManager.executeAsSystem().consumer((arg) -> {
			assertEquals(contextManager.getCurrentContext().getCurrentTenantId(), TEST_TENANT_ID);
			assertEquals(contextManager.getCurrentContext().getAuthenticated().getIdentityId(), "system");
			assertEquals(contextManager.getCurrentContext().getEffectiveAuthentication().getIdentityId(), "admin");
			assertEquals(arg, "test");
		}, "test");
		assertEquals(contextManager.getCurrentContext().getEffectiveAuthentication().getIdentityId(), "test");
		assertEquals(contextManager.getCurrentContext().getCurrentTenantId(), TEST_TENANT_ID);
	}

	@Test(expectedExceptions = ContextNotActiveException.class)
	public void test_executeAsSystem_shouldFailIfContextNotActive() throws Exception {
		contextManager.executeAsSystem().executable(() -> fail("This should not be possible to be executed"));
	}

	@Test(expectedExceptions = SecurityException.class)
	public void test_executeAsSystem_shouldFailIfExecutedAsSystemTenant() throws Exception {
		when(securityConfiguration.getSystemAdminUser())
				.thenReturn(new UserMock("systemadmin", SecurityContext.SYSTEM_TENANT));

		contextManager.initializeExecutionAsSystemAdmin();

		contextManager.executeAsSystem().executable(() -> fail("This should not be possible to be executed"));
	}

	@Test
	public void shouldNotBePossibleToChangeToSystemAdminFromOtherTenant() throws Exception {

		AuthenticationContext authenticationContext = AuthenticationContext
				.create(Collections.singletonMap("name", "test"));
		when(authenticator.authenticate(authenticationContext)).thenReturn(new UserMock("test", TEST_TENANT_ID));

		contextManager.initializeExecution(authenticationContext);

		when(securityConfiguration.getSystemUser())
				.thenReturn(new ConfigurationPropertyMock<User>(new UserMock("system", TEST_TENANT_ID)));
		when(securityConfiguration.getAdminUser())
				.thenReturn(new ConfigurationPropertyMock<User>(new UserMock("admin", TEST_TENANT_ID)));
		when(securityConfiguration.getSystemAdminUser())
				.thenReturn(new UserMock("systemadmin", SecurityContext.SYSTEM_TENANT));

		contextManager.initializeExecutionAsSystemAdmin();

		contextManager.executeAs().executable(
				() -> assertEquals(contextManager.getCurrentContext().getCurrentTenantId(), TEST_TENANT_ID));

		assertEquals(contextManager.getCurrentContext().getEffectiveAuthentication().getIdentityId(), "test");
		assertEquals(contextManager.getCurrentContext().getCurrentTenantId(), TEST_TENANT_ID);
	}

	@AfterMethod
	@SuppressWarnings("static-method")
	public void cleanUp() {
		SecurityContextHolder.clear();
	}
}
