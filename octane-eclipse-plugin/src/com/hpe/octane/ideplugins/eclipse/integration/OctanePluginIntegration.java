package com.hpe.octane.ideplugins.eclipse.integration;

import org.eclipse.ui.IWorkbench;
import org.eclipse.ui.IWorkbenchPage;
import org.eclipse.ui.IWorkbenchWindow;
import org.eclipse.ui.PartInitException;
import org.eclipse.ui.PlatformUI;

import com.hpe.adm.octane.ideplugins.services.connection.ConnectionSettings;
import com.hpe.adm.octane.ideplugins.services.connection.UserAuthentication;
import com.hpe.adm.octane.ideplugins.services.filtering.Entity;
import com.hpe.octane.ideplugins.eclipse.Activator;
import com.hpe.octane.ideplugins.eclipse.ui.entitydetail.EntityModelEditor;
import com.hpe.octane.ideplugins.eclipse.ui.entitydetail.EntityModelEditorInput;

public class OctanePluginIntegration {

    public static void setConnectionSettings(String baseUrl, Long sharedSpaceId, Long workspaceId, String username, String password) {
        ConnectionSettings connectionSettings = new ConnectionSettings(
                baseUrl,
                sharedSpaceId,
                workspaceId,
                new UserAuthentication(username, password));
        
        Activator.setConnectionSettings(connectionSettings);
    }

    public static void openDetailTab(Entity entityType, Long entityId) {
        IWorkbench wb = PlatformUI.getWorkbench();
        IWorkbenchWindow win = wb.getActiveWorkbenchWindow();
        IWorkbenchPage page = win.getActivePage();

        EntityModelEditorInput entityModelEditorInput = new EntityModelEditorInput(entityId, entityType);
        try {
            page.openEditor(entityModelEditorInput, EntityModelEditor.ID);
        } catch (PartInitException ex) {
        }
    }

    public static void openDetailTab(String entityTypeString, Long entityId) {
        Entity entityType = Entity.getEntityType(entityTypeString);
        openDetailTab(entityType, entityId);
    }

}