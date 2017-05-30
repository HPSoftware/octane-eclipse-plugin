package com.hpe.octane.ideplugins.eclipse.ui.editor;

import java.awt.Desktop;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Collection;

import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.jobs.IJobChangeEvent;
import org.eclipse.core.runtime.jobs.JobChangeAdapter;
import org.eclipse.jface.dialogs.MessageDialog;
import org.eclipse.jface.resource.JFaceResources;
import org.eclipse.swt.SWT;
import org.eclipse.swt.browser.Browser;
import org.eclipse.swt.browser.LocationEvent;
import org.eclipse.swt.browser.LocationListener;
import org.eclipse.swt.custom.CLabel;
import org.eclipse.swt.custom.ScrolledComposite;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.graphics.Font;
import org.eclipse.swt.graphics.FontData;
import org.eclipse.swt.layout.FillLayout;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Event;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Listener;
import org.eclipse.swt.widgets.Text;
import org.eclipse.ui.IEditorInput;
import org.eclipse.ui.IEditorSite;
import org.eclipse.ui.ISharedImages;
import org.eclipse.ui.PartInitException;
import org.eclipse.ui.PlatformUI;
import org.eclipse.ui.forms.widgets.Form;
import org.eclipse.ui.forms.widgets.FormToolkit;
import org.eclipse.ui.forms.widgets.Section;
import org.eclipse.ui.part.EditorPart;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.hpe.adm.nga.sdk.model.EntityModel;
import com.hpe.adm.nga.sdk.model.FieldModel;
import com.hpe.adm.octane.services.EntityService;
import com.hpe.adm.octane.services.filtering.Entity;
import com.hpe.adm.octane.services.ui.FormLayout;
import com.hpe.adm.octane.services.ui.FormLayoutSection;
import com.hpe.adm.octane.services.util.Util;
import com.hpe.octane.ideplugins.eclipse.Activator;
import com.hpe.octane.ideplugins.eclipse.ui.combobox.CustomEntityComboBox;
import com.hpe.octane.ideplugins.eclipse.ui.combobox.CustomEntityComboBoxLabelProvider;
import com.hpe.octane.ideplugins.eclipse.ui.editor.job.ChangePhaseJob;
import com.hpe.octane.ideplugins.eclipse.ui.editor.job.GetEntityDetailsJob;
import com.hpe.octane.ideplugins.eclipse.ui.editor.job.SendCommentJob;
import com.hpe.octane.ideplugins.eclipse.ui.util.LoadingComposite;
import com.hpe.octane.ideplugins.eclipse.ui.util.StackLayoutComposite;
import com.hpe.octane.ideplugins.eclipse.util.EntityFieldsConstants;
import com.hpe.octane.ideplugins.eclipse.util.EntityIconFactory;
import com.hpe.octane.ideplugins.eclipse.util.InfoPopup;
import com.hpe.octane.ideplugins.eclipse.util.resource.ImageResources;
import com.hpe.octane.ideplugins.eclipse.util.resource.SWTResourceManager;

public class EntityModelEditor extends EditorPart {

    public static final String ID = "com.hpe.octane.ideplugins.eclipse.ui.EntityModelEditor"; //$NON-NLS-1$
    private static final String DESCRIPTION_FIELD = "description";
    private static EntityIconFactory entityIconFactoryForTabInfo = new EntityIconFactory(20, 20, 7);
    private static EntityIconFactory entityIconFactory = new EntityIconFactory(25, 25, 7);
    private static EntityService entityService = Activator.getInstance(EntityService.class);

    private EntityModel entityModel;
    private FieldModel currentPhase;
    private EntityModel selectedPhase;
    private EntityModelEditorInput input;
    private Collection<EntityModel> possibleTransitions;
    private boolean shouldShowPhase = true;
    private boolean shouldCommentsBeShown;
    private GetEntityDetailsJob getEntiyJob;
    private Text inputComments;
    private String comments;

    private Composite entityDetailsParentComposite;
    private LoadingComposite loadingComposite;
    private FormLayout octaneEntityForm;
    private Form sectionsParentForm;
    private FormToolkit formGenerator;
    private Composite headerAndEntityDetailsParent;
    private ScrolledComposite headerAndEntityDetailsScrollComposite;
    private final String GO_TO_BROWSER_DIALOG_MESSAGE = "\nYou can only provide a value for this field using ALM Octane in a browser."
            + "\nDo you want to do this now? ";

    public EntityModelEditor() {
    }

    @Override
    public void init(IEditorSite site, IEditorInput input) throws PartInitException {
        if (!(input instanceof EntityModelEditorInput)) {
            throw new RuntimeException("Wrong input");
        }
        this.input = (EntityModelEditorInput) input;
        setSite(site);
        setInput(input);
        setPartName(String.valueOf(this.input.getId()));
        setTitleImage(entityIconFactoryForTabInfo.getImageIcon(this.input.getEntityType()));
    }

    /**
     * Create contents of the editor part.
     * 
     * @param parent
     */
    @Override
    public void createPartControl(Composite parent) {
        parent.setBackground(SWTResourceManager.getColor(SWT.COLOR_WHITE));
        parent.setBackgroundMode(SWT.INHERIT_FORCE);

        StackLayoutComposite stackContainer = new StackLayoutComposite(parent, SWT.NONE);
        // set loading GIF until the data is loaded
        loadingComposite = new LoadingComposite(stackContainer, SWT.NONE);
        stackContainer.showControl(loadingComposite);

        // This job retrieves the necessary data for the details view
        getEntiyJob = new GetEntityDetailsJob("Retiving entity details", this.input.getEntityType(), this.input.getId());
        getEntiyJob.schedule();
        getEntiyJob.addJobChangeListener(new JobChangeAdapter() {

            @Override
            public void scheduled(IJobChangeEvent event) {
                Display.getDefault().asyncExec(() -> {
                    stackContainer.showControl(loadingComposite);
                });
            }

            @Override
            public void done(IJobChangeEvent event) {
                if (getEntiyJob.wasEntityRetrived()) {
                    entityModel = getEntiyJob.getEntiyData();
                    Display.getDefault().asyncExec(() -> {
                        entityModel = getEntiyJob.getEntiyData();
                        octaneEntityForm = getEntiyJob.getFormForCurrentEntity();
                        if (getEntiyJob.shouldShowPhase()) {
                            shouldShowPhase = true;
                            currentPhase = getEntiyJob.getCurrentPhase();
                            possibleTransitions = getEntiyJob.getPossibleTransitionsForCurrentEntity();
                        } else {
                            shouldShowPhase = false;
                        }
                        if (getEntiyJob.shouldCommentsBeShown()) {
                            shouldCommentsBeShown = true;
                            comments = getEntiyJob.getCommentsForCurrentEntity();
                        } else {
                            shouldCommentsBeShown = false;
                        }
                        // After the data is loaded the UI is created
                        createEntityDetailsView(stackContainer);
                        // After the UI is created it gets displayed
                        stackContainer.showControl(headerAndEntityDetailsScrollComposite);
                    });
                }
            }
        });
    }

    private void createEntityDetailsView(Composite parent) {
        headerAndEntityDetailsScrollComposite = new ScrolledComposite(parent, SWT.BORDER | SWT.H_SCROLL | SWT.V_SCROLL);
        headerAndEntityDetailsScrollComposite.setExpandHorizontal(true);
        headerAndEntityDetailsScrollComposite.setExpandVertical(true);

        headerAndEntityDetailsParent = new Composite(headerAndEntityDetailsScrollComposite, SWT.NONE);
        headerAndEntityDetailsParent.setLayout(new FillLayout(SWT.HORIZONTAL));
        headerAndEntityDetailsParent.setLayoutData(new GridData(SWT.FILL, SWT.FILL, false, true, 1, 1));

        createHeaderPanel(headerAndEntityDetailsParent);

        formGenerator = new FormToolkit(parent.getDisplay());

        Composite entityDetailsAndCommentsComposite = new Composite(headerAndEntityDetailsParent, SWT.NONE);
        entityDetailsAndCommentsComposite.setForeground(org.eclipse.wb.swt.SWTResourceManager.getColor(SWT.COLOR_LIST_SELECTION));
        entityDetailsAndCommentsComposite.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true, 1, 1));
        formGenerator.adapt(entityDetailsAndCommentsComposite);
        formGenerator.paintBordersFor(entityDetailsAndCommentsComposite);
        entityDetailsAndCommentsComposite.setLayout(new GridLayout(3, false));

        entityDetailsParentComposite = new Composite(entityDetailsAndCommentsComposite, SWT.NONE);
        entityDetailsParentComposite.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true, 1, 1));
        entityDetailsParentComposite.setLayout(new FillLayout(SWT.HORIZONTAL));
        formGenerator.adapt(entityDetailsParentComposite);
        formGenerator.paintBordersFor(entityDetailsParentComposite);

        sectionsParentForm = formGenerator.createForm(entityDetailsParentComposite);
        sectionsParentForm.getBody().setLayout(new GridLayout(1, false));

        if (shouldCommentsBeShown) {
            Label commentsSeparator = new Label(entityDetailsAndCommentsComposite, SWT.SEPARATOR | SWT.SHADOW_IN);
            commentsSeparator.setForeground(org.eclipse.wb.swt.SWTResourceManager.getColor(SWT.COLOR_TITLE_BACKGROUND_GRADIENT));
            commentsSeparator.setLayoutData(new GridData(SWT.FILL, SWT.FILL, false, true, 1, 1));
            formGenerator.adapt(commentsSeparator, true, true);

            Composite commentsParentComposite = new Composite(entityDetailsAndCommentsComposite, SWT.NONE);
            GridData gd_commentsParentComposite = new GridData(SWT.FILL, SWT.FILL, false, true, 1, 1);
            gd_commentsParentComposite.widthHint = 300;
            gd_commentsParentComposite.minimumWidth = 300;
            commentsParentComposite.setLayoutData(gd_commentsParentComposite);
            formGenerator.adapt(commentsParentComposite);
            formGenerator.paintBordersFor(commentsParentComposite);
            commentsParentComposite.setLayout(new GridLayout(1, false));

            CLabel commentsTitleLabel = new CLabel(commentsParentComposite, SWT.NONE);
            formGenerator.adapt(commentsTitleLabel, true, true);
            commentsTitleLabel.setText("Comments");
            commentsTitleLabel.setMargins(5, 0, 0, 0);

            Composite inputCommentAndSendButtonComposite = new Composite(commentsParentComposite, SWT.NONE);
            inputCommentAndSendButtonComposite.setLayoutData(new GridData(SWT.FILL, SWT.FILL, false, false, 1, 1));
            formGenerator.adapt(inputCommentAndSendButtonComposite);
            formGenerator.paintBordersFor(inputCommentAndSendButtonComposite);
            inputCommentAndSendButtonComposite.setLayout(new GridLayout(2, false));

            inputComments = new Text(inputCommentAndSendButtonComposite, SWT.NONE);
            inputComments.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, true, 1, 1));
            inputComments.setToolTipText("Add new comment");
            formGenerator.adapt(inputComments, true, true);

            Button postCommentBtn = new Button(inputCommentAndSendButtonComposite, SWT.NONE);
            postCommentBtn.setLayoutData(new GridData(SWT.LEFT, SWT.CENTER, false, true, 1, 1));
            postCommentBtn.addSelectionListener(new SelectionAdapter() {
                @Override
                public void widgetSelected(SelectionEvent e) {
                    postComment(inputComments.getText());
                }
            });
            formGenerator.adapt(postCommentBtn, true, true);
            postCommentBtn.setText("Send");

            Browser commentsPanel = new Browser(commentsParentComposite, SWT.NONE);
            commentsPanel.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true, 1, 1));
            formGenerator.adapt(commentsPanel);
            formGenerator.paintBordersFor(commentsPanel);
            commentsPanel.setText(comments);

            commentsPanel.addLocationListener(new LinkInterceptListener());
        }
        if (entityModel != null) {

            // create other Sections
            for (FormLayoutSection formSection : octaneEntityForm.getFormLayoutSections()) {
                createSectionsWithEntityData(formSection);
            }
            // Create Description Section
            createDescriptionFormSection();
        }

        headerAndEntityDetailsScrollComposite.setContent(headerAndEntityDetailsParent);
        headerAndEntityDetailsScrollComposite.setMinSize(headerAndEntityDetailsParent.computeSize(SWT.DEFAULT, SWT.DEFAULT));
    }

    private void createHeaderPanel(Composite parent) {
        headerAndEntityDetailsParent.setLayout(new GridLayout(1, false));
        Composite headerComposite = new Composite(parent, SWT.NONE);
        headerComposite.setBackground(SWTResourceManager.getColor(SWT.COLOR_TRANSPARENT));
        headerComposite.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false, 1, 1));
        headerComposite.setLayout(new GridLayout(6, false));

        Label entityIcon = new Label(headerComposite, SWT.NONE);
        entityIcon.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, false, false, 1, 1));
        entityIcon.setImage(entityIconFactory.getImageIcon(Entity.getEntityType(entityModel)));

        Label lblEntityName = new Label(headerComposite, SWT.NONE);
        lblEntityName.setBackground(SWTResourceManager.getColor(SWT.COLOR_TRANSPARENT));
        lblEntityName.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false, 1, 1));
        lblEntityName.setText(Util.getUiDataFromModel(entityModel.getValue(EntityFieldsConstants.FIELD_NAME)));
        Font boldFont = new Font(lblEntityName.getDisplay(), new FontData(JFaceResources.DEFAULT_FONT, 12, SWT.BOLD));
        lblEntityName.setFont(boldFont);

        if (shouldShowPhase) {
            Label lblCurrentPhase = new Label(headerComposite, SWT.NONE);
            lblCurrentPhase.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, false, false, 1, 1));
            lblCurrentPhase.setText(Util.getUiDataFromModel(currentPhase, EntityFieldsConstants.FIELD_NAME));

            CustomEntityComboBox<EntityModel> nextPhasesComboBox = new CustomEntityComboBox<EntityModel>(headerComposite);
            nextPhasesComboBox.addSelectionListener((phaseEntityModel, newSelection) -> {
                selectedPhase = newSelection;
            });
            nextPhasesComboBox.setLabelProvider(new CustomEntityComboBoxLabelProvider<EntityModel>() {

                @Override
                public String getSelectedLabel(EntityModel entityModelElement) {
                    return Util.getUiDataFromModel(entityModelElement.getValue("target_phase"), "name");
                }

                @Override
                public String getListLabel(EntityModel entityModelElement) {
                    return Util.getUiDataFromModel(entityModelElement.getValue("target_phase"), "name");
                }
            });

            nextPhasesComboBox.setContent(new ArrayList<>(possibleTransitions));

            nextPhasesComboBox.selectFirstItem();

            Button savePhase = new Button(headerComposite, SWT.NONE);
            savePhase.setImage(PlatformUI.getWorkbench().getSharedImages().getImage(ISharedImages.IMG_ETOOL_SAVE_EDIT));
            savePhase.addListener(SWT.Selection, new Listener() {

                @Override
                public void handleEvent(Event event) {
                    saveCurrentPhase();
                }
            });
        }
        Button refresh = new Button(headerComposite, SWT.NONE);
        refresh.setImage(ImageResources.REFRESH_16X16.getImage());
        refresh.addListener(SWT.Selection, new Listener() {

            @Override
            public void handleEvent(Event event) {
                getEntiyJob.schedule();
            }

        });
    }

    // STEP 3
    private void createDescriptionFormSection() {
        Section section = formGenerator.createSection(sectionsParentForm.getBody(),
                Section.DESCRIPTION | Section.TREE_NODE | Section.EXPANDED);
        section.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true, 1, 1));
        section.setLayout(new FillLayout(SWT.HORIZONTAL));
        section.setText("Description");

        Browser descriptionPanel = new Browser(section, SWT.NONE);
        String descriptionText = Util.getUiDataFromModel(entityModel.getValue(EntityFieldsConstants.FIELD_DESCRIPTION));
        if (descriptionText.isEmpty()) {
            descriptionPanel.setText("No description");
        } else {
            descriptionPanel.setText(descriptionText);
        }
        descriptionPanel.addLocationListener(new LinkInterceptListener());
        formGenerator.createCompositeSeparator(section);
        section.setClient(descriptionPanel);
    }

    // STEP 4
    private void createSectionsWithEntityData(FormLayoutSection formSection) {
        Section section = formGenerator.createSection(sectionsParentForm.getBody(),
                Section.DESCRIPTION | Section.TREE_NODE | Section.EXPANDED);
        section.setText(formSection.getSectionTitle());
        section.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, false, 1, 1));
        section.setExpanded(true);
        formGenerator.createCompositeSeparator(section);

        Composite sectionClient = new Composite(section, SWT.NONE);
        sectionClient.setLayout(new FillLayout(SWT.HORIZONTAL));

        Composite sectionClientLeft = new Composite(sectionClient, SWT.NONE);
        sectionClientLeft.setLayout(new GridLayout(2, false));
        Composite sectionClientRight = new Composite(sectionClient, SWT.NONE);
        sectionClientRight.setLayout(new GridLayout(2, false));
        for (int i = 0; i <= formSection.getFields().size() - 1; i += 2) {
            if (!DESCRIPTION_FIELD.equals(formSection.getFields().get(i).getName())) {
                String fieldName = formSection.getFields().get(i).getName();
                String fielValue = "";

                if (EntityFieldsConstants.FIELD_OWNER.equals(fieldName) || EntityFieldsConstants.FIELD_AUTHOR.equals(fieldName)
                        || EntityFieldsConstants.FIELD_TEST_RUN_RUN_BY.equals(fieldName)
                        || EntityFieldsConstants.FIELD_DETECTEDBY.equals(fieldName)) {
                    fielValue = Util.getUiDataFromModel(entityModel.getValue(fieldName), "full_name");
                } else {
                    fielValue = Util.getUiDataFromModel(entityModel.getValue(fieldName));
                }

                CLabel tempLabelLeft = new CLabel(sectionClientLeft, SWT.NONE);
                tempLabelLeft.setText(prettifyLabels(fieldName));
                tempLabelLeft.setFont(JFaceResources.getFontRegistry().getBold(JFaceResources.DEFAULT_FONT));
                tempLabelLeft.setMargins(5, 2, 5, 2);

                CLabel tempValuesLabelLeft = new CLabel(sectionClientLeft, SWT.NONE);
                tempValuesLabelLeft.setText(fielValue);
                tempValuesLabelLeft.setMargins(5, 2, 5, 2);
            }
            if (formSection.getFields().size() > i + 1 && null != formSection.getFields().get(i + 1)
                    && !DESCRIPTION_FIELD.equals(formSection.getFields().get(i + 1).getName())) {
                String fieldName = formSection.getFields().get(i + 1).getName();
                String fielValue = "";

                if (EntityFieldsConstants.FIELD_OWNER.equals(fieldName) || EntityFieldsConstants.FIELD_AUTHOR.equals(fieldName)
                        || EntityFieldsConstants.FIELD_TEST_RUN_RUN_BY.equals(fieldName)
                        || EntityFieldsConstants.FIELD_DETECTEDBY.equals(fieldName)) {
                    fielValue = Util.getUiDataFromModel(entityModel.getValue(fieldName), "full_name");
                } else {
                    fielValue = Util.getUiDataFromModel(entityModel.getValue(fieldName));
                }

                CLabel tempLabelRight = new CLabel(sectionClientRight, SWT.NONE);
                tempLabelRight.setText(prettifyLabels(fieldName));
                tempLabelRight.setFont(JFaceResources.getFontRegistry().getBold(JFaceResources.DEFAULT_FONT));
                tempLabelRight.setMargins(5, 2, 5, 2);

                CLabel tempValuesLabelRight = new CLabel(sectionClientRight, SWT.NONE);
                tempValuesLabelRight.setText(fielValue);
                tempValuesLabelRight.setMargins(5, 2, 5, 2);
            }
        }
        section.setClient(sectionClient);
    }

    private String prettifyLabels(String str1) {
        str1 = str1.replaceAll("_", " ");
        char[] chars = str1.toCharArray();
        chars[0] = Character.toUpperCase(chars[0]);
        for (int x = 1; x < chars.length; x++) {
            if (chars[x - 1] == ' ') {
                chars[x] = Character.toUpperCase(chars[x]);
            }
        }
        return new String(chars);
    }

    private void saveCurrentPhase() {
        ChangePhaseJob changePhaseJob = new ChangePhaseJob("Chaging phase of entity", entityModel, selectedPhase);
        changePhaseJob.schedule();
        changePhaseJob.addJobChangeListener(new JobChangeAdapter() {
            @Override
            public void done(IJobChangeEvent event) {
                Display.getDefault().asyncExec(() -> {
                    if (changePhaseJob.isPhaseChanged()) {
                        new InfoPopup("Phase Transition", "Phase was changed").open();
                    } else {
                        String errorMessage = changePhaseJob.getFailedReason();
                        String detailedErrorMessage = "";
                        if (errorMessage.contains("400")) {
                            try {
                                JsonParser jsonParser = new JsonParser();
                                JsonObject jsonObject = (JsonObject) jsonParser.parse(errorMessage.substring(errorMessage.indexOf("{")));
                                detailedErrorMessage = jsonObject.get("description_translated").getAsString();
                            } catch (Exception e1) {
                                // logger.debug("Failed to get JSON message from
                                // Octane Server" + e1.getMessage());
                            }
                        } else {
                            detailedErrorMessage = changePhaseJob.getFailedReason();
                        }
                        boolean shouldGoToBroeser = MessageDialog.openConfirm(Display.getCurrent().getActiveShell(), "Business rule violation",
                                "Phase changed failed \n " + detailedErrorMessage + "\n" + GO_TO_BROWSER_DIALOG_MESSAGE);
                        if (shouldGoToBroeser) {
                            entityService.openInBrowser(entityModel);
                        }

                    }
                    getEntiyJob.schedule();
                });
            }
        });
    }

    private void setEntiyData() {
        GetEntityDetailsJob getEntiyJob = new GetEntityDetailsJob("Retiving entity details", this.input.getEntityType(), this.input.getId());
        getEntiyJob.schedule();
        getEntiyJob.addJobChangeListener(new JobChangeAdapter() {
            @Override
            public void done(IJobChangeEvent event) {
                Display.getDefault().asyncExec(() -> {
                    if (getEntiyJob.wasEntityRetrived()) {
                        entityModel = getEntiyJob.getEntiyData();
                    }
                });
            }
        });
    }

    private void postComment(String text) {
        SendCommentJob sendCommentJob = new SendCommentJob("Sending Comments", entityModel, text);
        sendCommentJob.schedule();
        sendCommentJob.addJobChangeListener(new JobChangeAdapter() {
            @Override
            public void done(IJobChangeEvent event) {
                Display.getDefault().asyncExec(() -> {
                    if (sendCommentJob.isCommentsSaved()) {
                        new InfoPopup("Comments", "Comment send").open();
                    } else {
                        MessageDialog.openError(Display.getCurrent().getActiveShell(), "ERROR",
                                "Comments could not be send \n ");
                    }
                    getEntiyJob.schedule();
                });
            }
        });

    }

    private class LinkInterceptListener implements LocationListener {
        // method called when the user clicks a link but before the link is
        // opened.
        public void changing(LocationEvent event) {
            URI externalUrl = null;
            try {
                externalUrl = new URI(event.location);
            } catch (URISyntaxException e) {
                // TODO Auto-generated catch block
                e.printStackTrace();
            }
            try {
                Desktop.getDesktop().browse(externalUrl);
            } catch (IOException e) {
                // TODO Auto-generated catch block
                e.printStackTrace();
            }
            event.doit = false;
        }

        // method called after the link has been opened in place.
        public void changed(LocationEvent event) {
            // Not used in this example
        }
    }

    @Override
    public void setFocus() {
        // Set the focus
    }

    @Override
    public void doSave(IProgressMonitor monitor) {
        // Do the Save operation
    }

    @Override
    public void doSaveAs() {
        // Do the Save As operation
    }

    @Override
    public boolean isDirty() {
        return false;
    }

    @Override
    public boolean isSaveAsAllowed() {
        return false;
    }

}