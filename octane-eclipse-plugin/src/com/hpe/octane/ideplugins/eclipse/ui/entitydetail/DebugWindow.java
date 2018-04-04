package com.hpe.octane.ideplugins.eclipse.ui.entitydetail;

import org.eclipse.core.runtime.jobs.IJobChangeEvent;
import org.eclipse.core.runtime.jobs.JobChangeAdapter;
import org.eclipse.jface.dialogs.MessageDialog;
import org.eclipse.swt.SWT;
import org.eclipse.swt.graphics.Rectangle;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Monitor;
import org.eclipse.swt.widgets.Shell;

import com.hpe.adm.nga.sdk.exception.OctaneException;
import com.hpe.adm.nga.sdk.model.FieldModel;
import com.hpe.adm.octane.ideplugins.services.EntityService;
import com.hpe.adm.octane.ideplugins.services.connection.ConnectionSettings;
import com.hpe.adm.octane.ideplugins.services.filtering.Entity;
import com.hpe.octane.ideplugins.eclipse.Activator;
import com.hpe.octane.ideplugins.eclipse.ui.entitydetail.job.GetEntityModelJob;
import com.hpe.octane.ideplugins.eclipse.ui.entitydetail.job.UpdateEntityJob;
import com.hpe.octane.ideplugins.eclipse.ui.entitydetail.model.EntityModelWrapper;
import com.hpe.octane.ideplugins.eclipse.ui.entitydetail.model.EntityModelWrapper.FieldModelChangedHandler;
import com.hpe.octane.ideplugins.eclipse.ui.util.InfoPopup;
import com.hpe.octane.ideplugins.eclipse.ui.util.LoadingComposite;
import com.hpe.octane.ideplugins.eclipse.ui.util.StackLayoutComposite;
import com.hpe.octane.ideplugins.eclipse.ui.util.resource.SWTResourceManager;

import swing2swt.layout.BorderLayout;

public class DebugWindow {

    protected Shell shell;
    protected Display display;
    private EntityModelWrapper entityModelWrapper;
    
    private EntityComposite entityComposite;
    private LoadingComposite loadingComposite;
    private StackLayoutComposite rootComposite;

    public static void main(String[] args) {
        initMockActivator();
        try {
            DebugWindow window = new DebugWindow();
            window.open();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void open() {
        display = Display.getDefault();
        try {
            shell = new Shell(display);
            try {
                createContents();
                shell.open();
                while (!shell.isDisposed()) {
                    if (!display.readAndDispatch()) {
                        display.sleep();
                    }
                }
            } finally {
                if (!shell.isDisposed()) {
                    shell.dispose();
                }
            }
        } finally {
            display.dispose();
        }
        System.exit(0);
    }

    /**
     * Create contents of the window.
     */
    protected void createContents() {
        shell = new Shell();
        shell.setSize(1200, 800);
        shell.setLayout(new BorderLayout());
        shell.setBackground(SWTResourceManager.getColor(SWT.COLOR_WHITE));
        shell.setBackgroundMode(SWT.INHERIT_FORCE);
        positionShell();
        
        rootComposite = new StackLayoutComposite(shell, SWT.NONE);
        loadingComposite = new LoadingComposite(rootComposite, SWT.NONE);
        entityComposite = new EntityComposite(rootComposite, SWT.NONE);
        rootComposite.showControl(loadingComposite);
        
        loadEntity();

        entityComposite.addRefreshSelectionListener(event -> {
            loadEntity();
        });
        entityComposite.addSaveSelectionListener(event -> {
            saveEntity();
        });
    }

    protected void loadEntity() {
        GetEntityModelJob getEntityDetailsJob = new GetEntityModelJob("Retrieving entity details", Entity.valueOf(System.getProperty("entity")), Long.parseLong(System.getProperty("entityId")));

        getEntityDetailsJob.addJobChangeListener(new JobChangeAdapter() {
            @Override
            public void scheduled(IJobChangeEvent event) {
                Display.getDefault().asyncExec(() -> {
                    rootComposite.showControl(loadingComposite);
                });
            }

            @Override
            public void done(IJobChangeEvent event) {
                if (getEntityDetailsJob.wasEntityRetrived()) {
                    entityModelWrapper = new EntityModelWrapper(getEntityDetailsJob.getEntiyData());
                    
                  
                    
                    entityModelWrapper.addFieldModelChangedHandler(new FieldModelChangedHandler() {
                        @Override
                        public void fieldModelChanged(@SuppressWarnings("rawtypes") FieldModel fieldModel) {
                            Display.getDefault().asyncExec(() -> {
                                shell.setText("*" + shell.getText());
                            });
                        }
                    });
                    
                    Display.getDefault().asyncExec(() -> {
                        shell.setText("mata");
                        entityComposite.setEntityModel(entityModelWrapper);
                        rootComposite.showControl(entityComposite);
                    });
                } else {
                    MessageDialog.openError(Display.getCurrent().getActiveShell(), "Error",
                            "Failed to load entity, " + getEntityDetailsJob.getException());
                }
            }
        });

        getEntityDetailsJob.schedule();
    }

    private void saveEntity() {
        EntityService entityService = Activator.getInstance(EntityService.class);

        UpdateEntityJob updateEntityJob = new UpdateEntityJob("Saving " + entityModelWrapper.getEntityType(), entityModelWrapper.getEntityModel());
        updateEntityJob.schedule();
        updateEntityJob.addJobChangeListener(new JobChangeAdapter() {
            @Override
            public void done(IJobChangeEvent event) {
                Display.getDefault().asyncExec(() -> {
                    OctaneException octaneException = updateEntityJob.getOctaneException();
                    if(octaneException == null){
                        new InfoPopup("Saving entity", "Saved your changes").open();
                    } else {
                        EntityDetailErrorDialog errorDialog = new EntityDetailErrorDialog(shell);
                        
                        errorDialog.addButton("Back", ()-> errorDialog.close());
                        errorDialog.addButton("Refresh", ()-> {
                            loadEntity();
                            errorDialog.close();
                        });
                        errorDialog.addButton("Open in browser", ()-> {
                            entityService.openInBrowser(entityModelWrapper.getReadOnlyEntityModel());
                            errorDialog.close();
                        });
                        
                        errorDialog.open(octaneException, "Saving entity failed");
                    }
                });
            }
        });
    }

    private void positionShell() {
        // Monitor monitor = display.getPrimaryMonitor();
        Monitor monitor = display.getMonitors()[1];
        Rectangle bounds = monitor.getBounds();
        Rectangle rect = shell.getBounds();

        int x = bounds.x + (bounds.width - rect.width) / 2;
        int y = bounds.y + (bounds.height - rect.height) / 2;
        shell.setLocation(x, y);
    }

    private static void initMockActivator() {
        ConnectionSettings connectionSettings = new ConnectionSettings();
        connectionSettings.setBaseUrl(System.getProperty("url"));
        connectionSettings.setSharedSpaceId(Long.parseLong(System.getProperty("ssid")));
        connectionSettings.setWorkspaceId(Long.parseLong(System.getProperty("wsid")));
        connectionSettings.setUserName(System.getProperty("username"));
        connectionSettings.setPassword(System.getProperty("password"));
        Activator.setConnectionSettings(connectionSettings);
    }

}