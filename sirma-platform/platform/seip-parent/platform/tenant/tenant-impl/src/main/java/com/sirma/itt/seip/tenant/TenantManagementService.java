package com.sirma.itt.seip.tenant;

import java.lang.invoke.MethodHandles;
import java.util.Collection;
import java.util.Deque;
import java.util.LinkedList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.sirma.itt.seip.configuration.db.Configuration;
import com.sirma.itt.seip.configuration.db.ConfigurationManagement;
import com.sirma.itt.seip.plugin.ExtensionPoint;
import com.sirma.itt.seip.runtime.boot.Startup;
import com.sirma.itt.seip.tenant.TenantInitializationStatusService.Status;
import com.sirma.itt.seip.tenant.context.Tenant;
import com.sirma.itt.seip.tenant.context.TenantInfo;
import com.sirma.itt.seip.tenant.context.TenantManager;
import com.sirma.itt.seip.tenant.context.TenantStatus;
import com.sirma.itt.seip.tenant.exception.TenantOperationException;
import com.sirma.itt.seip.tenant.exception.TenantValidationException;
import com.sirma.itt.seip.tenant.wizard.TenantDeletionContext;
import com.sirma.itt.seip.tenant.wizard.TenantInitializationContext;
import com.sirma.itt.seip.tenant.wizard.TenantInitializationModel;
import com.sirma.itt.seip.tenant.wizard.TenantStep;
import com.sirma.itt.seip.tenant.wizard.TenantStepData;
import com.sirma.itt.seip.tenant.wizard.exception.TenantCreationException;
import com.sirma.itt.seip.tenant.wizard.exception.TenantDeletionException;
import com.sirma.itt.seip.tenant.wizard.exception.TenantUpdateException;
import com.sirma.itt.seip.time.TimeTracker;
import com.sirma.itt.seip.tx.TransactionSupport;

/**
 * The service executes the steps in order.
 *
 * @author bbanchev
 */
@ApplicationScoped
public class TenantManagementService {
	private static final Logger LOGGER = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

	@Inject
	@ExtensionPoint(value = TenantStep.CREATION_STEP_NAME)
	private Iterable<TenantStep> steps;

	@Inject
	@ExtensionPoint(value = TenantStep.DELETION_STEP_NAME)
	private Iterable<TenantStep> deletionSteps;

	@Inject
	@ExtensionPoint(value = TenantStep.UPDATE_STEP_NAME)
	private Iterable<TenantStep> updateSteps;

	@Inject
	private TenantManager tenantManager;

	@Inject
	private TenantInitializationStatusService statusService;

	@Inject
	private TransactionSupport transactionSupport;

	@Inject
	private ConfigurationManagement configurationManagement;

	/**
	 * Execute the step creation. The model is expected to be updated with all required data. If any error in step
	 * occurs all rollback methods for already executed steps are invoked as well on the current
	 *
	 * @param models
	 *            the populated models
	 */
	@SuppressWarnings("boxing")
	public void create(TenantInitializationModel models) {
		TenantInitializationContext tenantInitializationContext = new TenantInitializationContext();
		tenantInitializationContext.setMode(TenantInitializationContext.Mode.CREATE);

		Deque<TenantStep> executed = new LinkedList<>();
		TimeTracker timeTracker = TimeTracker.createAndStart();
		LOGGER.info("Beginning new tenant creation..");
		String tenantId = models.get("TenantInitialization").getPropertyValue("tenantid", true);
		try {
			steps.forEach(step -> {
				statusService.setStatus(tenantId, Status.IN_PROGRESS, step.getIdentifier());
				invokeStep(step, models, tenantInitializationContext, executed);
			});
			LOGGER.info("Tenant creation completed. Process took {} ms", timeTracker.stop());
		} catch (Exception e) {
			statusService.setStatus(tenantId, Status.FAILED, e.getMessage() + " Rolling back tenant");
			LOGGER.error("Tenant creation failure due to {} - going to rollback!", e.getMessage(), e);
			transactionSupport
					.invokeInNewTx(() -> rollback(executed, tenantInitializationContext.getTenantInfo(), models));
			throw new TenantCreationException("Tenant creation failure!", e);
		}
		statusService.setStatus(tenantId, Status.COMPLETED, "Successful tenant creation!");
	}

	/**
	 * Execute all tenant delete steps. The model for the deleted tenant is expected to be correct (All needed
	 * configurations present, all servers up and running), because if the tenant deletion fails at some step, it might
	 * leave the tenant in an inconsistent state.
	 * <p>
	 * This might happen because we can't provide a rollback for the tenant deletion, since not all data that was needed
	 * for the tenant creation might be available at this point in time.
	 * </p>
	 *
	 * @param tenantId
	 *            the tenant id
	 */
	@SuppressWarnings("boxing")
	public void delete(String tenantId) {
		LOGGER.info("Beginning tenant deletion of {}", tenantId);
		TenantInfo info = new TenantInfo(tenantId);
		// Call the tenant removed listeners first. This will ensure that all currently running
		// threads/operations on the tenant will be closed before deleting the resource that they
		// are trying to use.
		callTenantRemovedListeners(tenantId);

		List<String> failedMessages = new LinkedList<>();
		invokeDeleteSteps(info, failedMessages);
		markTenantAsDeleted(tenantId, failedMessages);

		if (failedMessages.isEmpty()) {
			statusService.setStatus(tenantId, Status.COMPLETED, "Tenant Deleted!");
		} else {
			statusService.setStatus(tenantId, Status.FAILED, failedMessages.stream()
					.map(msg -> "<li>" + msg + "</li>")
					.collect(Collectors.joining("", "<ul>", "</ul>")));
		}
	}

	private void callTenantRemovedListeners(String tenantId) {
		try {
			tenantManager.callTenantRemovedListeners(tenantId);
		} catch (TenantOperationException e) {
			LOGGER.error("Failed tenant remove listeners", e);
		}
	}

	private void invokeDeleteSteps(TenantInfo info, List<String> failedMessages) {
		TenantDeletionContext deletionContext = buildDeletionContext(info, false,
				configurationManagement::getAllConfigurations);
		deletionSteps.forEach(step -> {
			LOGGER.info("Tenant deletion at step {}.", step.getIdentifier());
			TenantStepData data = TenantStepData.createEmpty(step.getIdentifier());
			// We set this because some step require that the tenant creation step has been
			// completed successfully before continuing and since the tenant has already been
			// created we can safely assume that.
			data.completedSuccessfully();
			statusService.setStatus(info.getTenantId(), Status.IN_PROGRESS, step.getIdentifier());
			try {
				// some steps could take more time to delete
				// data source removing could take up to 5 minutes
				transactionSupport.invokeInNewTx(() -> step.delete(data, deletionContext), 10,
						TimeUnit.MINUTES);
			} catch (Exception e) {
				LOGGER.warn("Tenant deletion failure at step {} due to {}!", step.getIdentifier(), e.getMessage(), e);
				failedMessages.add(String.format("Tenant delete step %s failed die to: %s", step.getIdentifier(), e.getMessage()));
			}
		});
	}

	private TenantDeletionContext buildDeletionContext(TenantInfo info, boolean rollback,
			Supplier<Collection<Configuration>> configurationsSupplier) {
		TenantDeletionContext deletionContext = new TenantDeletionContext(info, rollback);

		List<Configuration> tenantConfigurations = configurationsSupplier.get()
				.stream()
				.filter(config -> config.getTenantId().equals(info.getTenantId()))
				.collect(Collectors.toList());
		deletionContext.setConfigurations(tenantConfigurations);

		return deletionContext;
	}

	private void markTenantAsDeleted(String tenantId, List<String> failedMessages) {
		try {
			transactionSupport.invokeInNewTx(() -> {
				try {
					tenantManager.markTenantForDeletion(tenantId);
				} catch (TenantValidationException e) {
					LOGGER.warn("Tenant deletion failure due to {}!", e.getMessage(), e);
					failedMessages.add(e.getMessage());
					throw new TenantDeletionException("Tenant deletion failure!", e);
				}
			});
		} catch (TenantDeletionException e) {
			// used to rollback the mark operation so the exception is not relevant
			LOGGER.trace("", e);
		}
	}

	/**
	 * Delete all marked for deletion tenants. Note that this will just remove all tenants from the tenants table with
	 * status {@link TenantStatus#DELETED}, so the tenant ids can be reused. This won't invoke any actual tenant
	 * deletion.
	 */
	@Startup
	void deleteMarkedTenants() {
		tenantManager.deleteMarkedTenants();
	}

	/**
	 * Execute update steps. The model is expected to be updated with all required data. If any error in step occurs all
	 * rollback methods for already executed steps are invoked as well on the current
	 *
	 * @param models
	 *            the populated models
	 * @param tenantId
	 *            Id of the tenant
	 */
	@SuppressWarnings("boxing")
	public void update(TenantInitializationModel models, String tenantId) {
		Tenant tenant = tenantManager.getTenant(tenantId)
				.orElseThrow(() -> new TenantUpdateException("Tenant update should be called on existing tenant"));

		TenantInitializationContext tenantInitializationContext = new TenantInitializationContext();
		tenantInitializationContext.setTenantInfo(tenant.toTenantInfo());
		tenantInitializationContext.setMode(TenantInitializationContext.Mode.UPDATE);

		Deque<TenantStep> executed = new LinkedList<>();
		TimeTracker timeTracker = TimeTracker.createAndStart();
		LOGGER.info("Begin updating tenant with id: {}", tenantId);
		try {
			updateSteps.forEach(step -> {
				statusService.setStatus(tenantId, Status.IN_PROGRESS, step.getIdentifier());
				invokeStep(step, models, tenantInitializationContext, executed);
			});
			// notify observers
			tenantManager.finishTenantActivation(tenantId);
			LOGGER.info("Tenant {} update completed. Process took {} ms", tenantId, timeTracker.stop());
		} catch (Exception e) {
			LOGGER.error("Tenant {} update failure due to {} - going to rollback!", tenantId, e.getMessage(), e);
			statusService.setStatus(tenantId, Status.FAILED, e.getMessage());
			transactionSupport
					.invokeInNewTx(() -> rollback(executed, tenantInitializationContext.getTenantInfo(), models));
			throw new TenantCreationException("Tenant " + tenantId + " update failure!", e);
		}
		statusService.setStatus(tenantId, Status.COMPLETED, "Successful tenant update!");
	}

	private static void invokeStep(TenantStep tenantStep, TenantInitializationModel models,
			TenantInitializationContext tenantInitializationContext, Deque<TenantStep> executed) {
		String stepName = tenantStep.getIdentifier();

		LOGGER.info("Processing step: {}...", stepName);
		executed.push(tenantStep);

		TenantStepData current = models.get(stepName);
		Objects.requireNonNull(current, "Unidentified tenant step " + stepName);

		tenantStep.execute(current, tenantInitializationContext);
		LOGGER.info("Step: {} is completed!", stepName);
	}

	private void rollback(Deque<TenantStep> executed, TenantInfo tenantInfo, TenantInitializationModel models) {
		TenantStep step;
		TenantDeletionContext deletionContext = buildDeletionContext(tenantInfo, true,
				configurationManagement::getAllConfigurations);

		while (!executed.isEmpty()) {
			step = executed.pop();
			try {
				LOGGER.info("Rolling back step: {}", step.getIdentifier());
				step.delete(models.get(step.getIdentifier()), deletionContext);
			} catch (Exception e) {
				LOGGER.warn("Rollback step {} failed with: {}", step.getIdentifier(), e.getMessage(), e);
			}
		}
		LOGGER.info("Rollback complete!");
	}

	/**
	 * Gets the model for tenant initialization. It is a new blank model that is legitimate for step update and for
	 * later invocation of {@link #create(TenantInitializationModel)}
	 *
	 * @return the model to be updated before {@link #create(TenantInitializationModel)}
	 */
	public TenantInitializationModel provideModel() {
		TenantInitializationModel model = new TenantInitializationModel();
		steps.iterator().forEachRemaining(step -> model.add(step.provide()));
		return model;
	}

	/**
	 * Get all tenants
	 *
	 * @return List with tenants
	 */
	public Collection<Tenant> getTenants() {
		return tenantManager.getAllTenants();
	}
}
