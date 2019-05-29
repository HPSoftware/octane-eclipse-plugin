/****************************************************************************
 *
 *   Copyright (C) Micro Focus.
 *   All rights reserved. See the copyright notice at the top of this source
 *   structure for further details.
 *
 *   $Id: mf.eclipse.codetemplates.xml 943199 2019-01-03 12:31:44Z nicolasp $
 ****************************************************************************/

package com.hpe.octane.ideplugins.eclipse.awm;

import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.swt.widgets.Display;

import com.hpe.octane.ideplugins.eclipse.integration.OctanePluginIntegration;
import com.microfocus.awm.control.toolexecution.ToolResult;
import com.microfocus.awm.control.toolexecution.ToolUtility;
import com.microfocus.awm.core.TaurusToolException;
import com.microfocus.awm.model.toolexecution.IMassProcessingToolContext;
import com.microfocus.awm.model.toolexecution.IStringInputParameter;
import com.microfocus.awm.model.toolexecution.IToolContext;
import com.microfocus.awm.model.toolexecution.IToolExecutor2;
import com.microfocus.awm.model.toolexecution.IToolResult;

public class OpenEntityEditorToolExecutor implements IToolExecutor2 {
	
	private static final String PARM_ENTITY_ID = "in.entityId";
	private static final String PARM_ENTITY_TYPE = "in.entityType";

	@Override
	public IToolResult executeSingleProcessing(final IToolContext pPToolContext, final IProgressMonitor pPMon) throws TaurusToolException {

		String entityIdString = ToolUtility
				.getInputParameter(pPToolContext, PARM_ENTITY_ID, IStringInputParameter.class).getParameterValue();
		
		String entityType = ToolUtility.getInputParameter(pPToolContext, PARM_ENTITY_TYPE, IStringInputParameter.class)
				.getParameterValue();

		long entityId;
		
		try {
			entityId = Long.valueOf(entityIdString);
		} catch (NumberFormatException e) {
			throw new TaurusToolException("Entity ID \"" + entityIdString + "\" is not a number!");
		}

		/* Open entity in the Octane entity model editor */
		try {
			openEntityEditor(entityId, entityType);
		} catch (RuntimeException | NoClassDefFoundError e) {
			throw new TaurusToolException("Failed to open the entity \"" + entityId + "\" of type \"" + entityType
					+ "\" in the EntityModelEditor.", e);
		}
		return new ToolResult(pPToolContext);
	}

	private void openEntityEditor(final long pEntityId, final String pEntityType) throws TaurusToolException {
		final Exception[] exceptionHolder = new Exception[1];
		Display.getDefault().syncExec(new Runnable() {
			@Override
			public void run() {
				OctanePluginIntegration.openDetailTab(pEntityType, pEntityId);
			}
		});
		Exception exception = exceptionHolder[0];
		if (exception != null)
			throw new TaurusToolException(exception);
	}

	@Override
	public IToolResult executeMassProcessing(final IMassProcessingToolContext pPMassProcessingContext,
			final IProgressMonitor pPMon) throws TaurusToolException {
		return null;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see
	 * com.microfocus.awm.model.toolexecution.IToolExecutor2#supportsMassProcessing(
	 * )
	 */
	@Override
	public boolean supportsMassProcessing() {
		return false;
	}

}