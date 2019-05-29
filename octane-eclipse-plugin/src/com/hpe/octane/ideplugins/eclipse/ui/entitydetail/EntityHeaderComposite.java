/*******************************************************************************
 * © 2017 EntIT Software LLC, a Micro Focus company, L.P.
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *  http://www.apache.org/licenses/LICENSE-2.0
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 ******************************************************************************/
package com.hpe.octane.ideplugins.eclipse.ui.entitydetail;


import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.eclipse.jface.resource.JFaceResources;
import org.eclipse.jface.viewers.LabelProvider;
import org.eclipse.swt.SWT;
import org.eclipse.swt.custom.StyledText;
import org.eclipse.swt.events.FocusAdapter;
import org.eclipse.swt.events.FocusEvent;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.graphics.Font;
import org.eclipse.swt.graphics.FontData;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Listener;
import org.eclipse.ui.ISharedImages;

import com.hpe.adm.nga.sdk.metadata.FieldMetadata;
import com.hpe.adm.nga.sdk.metadata.FieldMetadata.FieldType;
import com.hpe.adm.nga.sdk.model.EntityModel;
import com.hpe.adm.octane.ideplugins.services.EntityService;
import com.hpe.adm.octane.ideplugins.services.MetadataService;
import com.hpe.adm.octane.ideplugins.services.filtering.Entity;
import com.hpe.adm.octane.ideplugins.services.model.EntityModelWrapper;
import com.hpe.adm.octane.ideplugins.services.util.DefaultEntityFieldsUtil;
import com.hpe.octane.ideplugins.eclipse.Activator;
import com.hpe.octane.ideplugins.eclipse.preferences.PluginPreferenceStorage;
import com.hpe.octane.ideplugins.eclipse.ui.comment.job.GetCommentsJob;
import com.hpe.octane.ideplugins.eclipse.ui.entitydetail.field.EntityPhaseComposite;
import com.hpe.octane.ideplugins.eclipse.ui.entitydetail.field.StringFieldEditor;
import com.hpe.octane.ideplugins.eclipse.ui.entitydetail.job.GetPossiblePhasesJob;
import com.hpe.octane.ideplugins.eclipse.ui.util.MultiSelectComboBox;
import com.hpe.octane.ideplugins.eclipse.ui.util.icon.EntityIconFactory;
import com.hpe.octane.ideplugins.eclipse.ui.util.resource.ImageResources;
import com.hpe.octane.ideplugins.eclipse.ui.util.resource.PlatformResourcesManager;
import com.hpe.octane.ideplugins.eclipse.ui.util.resource.SWTResourceManager;
import com.hpe.octane.ideplugins.eclipse.util.DelayedRunnable;
import com.hpe.octane.ideplugins.eclipse.util.EntityFieldsConstants;

public class EntityHeaderComposite extends Composite {

    private static final String TOOLTIP_REFRESH = "Refresh entity details";
    private static final String TOOLTIP_PHASE = "Save changes";
    private static final String TOOLTIP_FIELDS = "Customize fields to be shown";
    private static final String TOOLTIP_COMMENTS = "Show comments";
    private static final String TOOLTIP_VIEW_IN_BROWSER = "View in browser (System)";

    private static MetadataService metadataService = Activator.getInstance(MetadataService.class);
    private Map<String, String> fieldLabelMap;
    private static final Map<Entity, Set<String>> defaultFields = DefaultEntityFieldsUtil.getDefaultFields();

    private Label lblEntityIcon;
    private StyledText txtEntityId;
    private StringFieldEditor nameFieldEditor;

    private EntityModelWrapper entityModelWrapper;

    private EntityPhaseComposite phaseComposite;

    private Button btnRefresh;
    private Button btnSave;
    private Button btnFields;
    private Button btnComments;
    private Button btnBrowser;

    private MultiSelectComboBox<String> fieldCombo;

    private GridData gdBtnComments;

    /**
     * Create the composite.
     * 
     * @param parent
     * @param style
     */
    public EntityHeaderComposite(Composite parent, int style) {
        super(parent, style);
        setLayout(new GridLayout(10, false));
        
        Font boldFont = new Font(getDisplay(), new FontData(JFaceResources.DEFAULT_FONT, 11, SWT.BOLD));
        
        lblEntityIcon = new Label(this, SWT.NONE);
        lblEntityIcon.setLayoutData(new GridData(SWT.LEFT, SWT.FILL, false, false, 1, 1));

        txtEntityId = new StyledText(this, SWT.NONE);
        txtEntityId.setEditable(false);
        txtEntityId.setFont(boldFont);
        txtEntityId.addFocusListener(new FocusAdapter() {
            @Override
            public void focusLost(FocusEvent e) {
                //clear selection on focus loss
                txtEntityId.setSelection(0, 0);
            }
        });
        txtEntityId.setCaret(null);

        Label lblSeparator = new Label(this, SWT.SEPARATOR | SWT.VERTICAL);
        GridData lblSeparatorGridData = new GridData(SWT.LEFT, SWT.CENTER, false, false, 1, 1);
        lblSeparatorGridData.heightHint = 16;
        lblSeparator.setLayoutData(lblSeparatorGridData);

        nameFieldEditor = new StringFieldEditor(this, SWT.NONE);
        nameFieldEditor.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false, 1, 1));
        nameFieldEditor.setBackground(SWTResourceManager.getColor(SWT.COLOR_TRANSPARENT));

        phaseComposite = new EntityPhaseComposite(this, SWT.NONE);
        phaseComposite.setLayoutData(new GridData(SWT.CENTER, SWT.CENTER, false, false, 1, 1));

        btnSave = new Button(this, SWT.NONE);
        btnSave.setLayoutData(new GridData(SWT.CENTER, SWT.CENTER, false, false, 1, 1));
        btnSave.setToolTipText(TOOLTIP_PHASE);
        btnSave.setImage(PlatformResourcesManager.getPlatformImage(ISharedImages.IMG_ETOOL_SAVE_EDIT));
        btnSave.setEnabled(false);

        btnRefresh = new Button(this, SWT.NONE);
        btnRefresh.setLayoutData(new GridData(SWT.CENTER, SWT.CENTER, false, false, 1, 1));
        btnRefresh.setImage(ImageResources.REFRESH_16X16.getImage());
        btnRefresh.setToolTipText(TOOLTIP_REFRESH);

        btnFields = new Button(this, SWT.NONE);
        btnFields.setLayoutData(new GridData(SWT.CENTER, SWT.CENTER, false, false, 1, 1));
        btnFields.setToolTipText(TOOLTIP_FIELDS);

        btnBrowser = new Button(this, SWT.NONE);
        btnBrowser.setLayoutData(new GridData(SWT.CENTER, SWT.CENTER, false, false, 1, 1));
        btnBrowser.setImage(ImageResources.BROWSER_16X16.getImage());
        btnBrowser.setToolTipText(TOOLTIP_VIEW_IN_BROWSER);
        btnBrowser.addListener(SWT.MouseDown,
                event -> Activator.getInstance(EntityService.class).openInBrowser(entityModelWrapper.getReadOnlyEntityModel()));

        btnComments = new Button(this, SWT.NONE);
        gdBtnComments = new GridData(SWT.CENTER, SWT.CENTER, false, false, 1, 1);
        btnComments.setLayoutData(gdBtnComments);
        btnComments.setImage(ImageResources.COMMENTS_16X16.getImage());
        btnComments.setToolTipText(TOOLTIP_COMMENTS);

        // Actual data is populated when entity is set
        fieldCombo = new MultiSelectComboBox<>(new LabelProvider() {
            @Override
            public String getText(Object fieldName) {
                return fieldLabelMap.get(fieldName);
            }
        });

        btnFields.addListener(SWT.Selection, event -> {
            fieldCombo.showFloatShell(btnFields);
            fieldCombo.setSelection(PluginPreferenceStorage.getShownEntityFields(entityModelWrapper.getEntityType()), false);
        });

        fieldCombo.setResetRunnable(() -> {
            fieldCombo.setSelection(defaultFields.get(entityModelWrapper.getEntityType()));
        });

        DelayedRunnable delayedRunnable = new DelayedRunnable(() -> {
            Display.getDefault().asyncExec(() -> {

                PluginPreferenceStorage.setShownEntityFields(
                        entityModelWrapper.getEntityType(),
                        new LinkedHashSet<>(fieldCombo.getSelections()));

                if (PluginPreferenceStorage.areShownEntityFieldsDefaults(entityModelWrapper.getEntityType())) {
                    btnFields.setImage(ImageResources.FIELDS_OFF.getImage());
                } else {
                    btnFields.setImage(ImageResources.FIELDS_ON.getImage());
                }
            });
        }, 500);

        fieldCombo.addSelectionListener(new SelectionAdapter() {
            @Override
            public void widgetSelected(SelectionEvent e) {
                delayedRunnable.execute();
            }
        });
    }

    public void setEntityModel(EntityModelWrapper entityModelWrapper) {
        this.entityModelWrapper = entityModelWrapper;

        phaseComposite.setEntityModel(entityModelWrapper);

        lblEntityIcon.setImage(EntityIconFactory.getInstance().getImageIcon(entityModelWrapper.getEntityType(), 25, 8));

        txtEntityId.setText(entityModelWrapper.getReadOnlyEntityModel().getId());

        nameFieldEditor.setField(entityModelWrapper, EntityFieldsConstants.FIELD_NAME);

        selectFieldsToDisplay(entityModelWrapper.getReadOnlyEntityModel());

        if (GetCommentsJob.hasCommentSupport(entityModelWrapper.getEntityType())) {
            btnComments.setVisible(true);
            gdBtnComments.exclude = false;
        } else {
            btnComments.setVisible(false);
            gdBtnComments.exclude = true;
        }

        if (GetPossiblePhasesJob.hasPhases(entityModelWrapper.getEntityType())) {
            setChildVisibility(phaseComposite, true);
        } else {
            setChildVisibility(phaseComposite, false);
        }
        
        //The save button should only be enabled after the entity is dirty
        btnSave.setEnabled(false);
        entityModelWrapper.addFieldModelChangedHandler(fieldModel -> btnSave.setEnabled(true));

        layout();
        update();
    }

    private void setChildVisibility(Control control, boolean isVisible) {
        control.setVisible(isVisible);
    }

    public void addSaveSelectionListener(Listener listener) {
        btnSave.addListener(SWT.Selection, listener);
    }

    public void addRefreshSelectionListener(Listener listener) {
        btnRefresh.addListener(SWT.Selection, listener);
    }

    public void addCommentsSelectionListener(Listener listener) {
        btnComments.addListener(SWT.Selection, listener);
    }

    private void selectFieldsToDisplay(EntityModel entityModel) {
        if (PluginPreferenceStorage.areShownEntityFieldsDefaults(Entity.getEntityType(entityModel))) {
            btnFields.setImage(ImageResources.FIELDS_OFF.getImage());
        } else {
            btnFields.setImage(ImageResources.FIELDS_ON.getImage());
        }

        // make a map of the field names and labels
        Collection<FieldMetadata> fieldMetadata = metadataService.getVisibleFields(Entity.getEntityType(entityModel));
        
        // do not include memo fields as they are not supported, for the EntityFieldsComposite ui
        fieldLabelMap = fieldMetadata
                .stream()
                .filter(metadata -> metadata.getFieldType() != FieldType.Memo)
                .collect(Collectors.toMap(FieldMetadata::getName, FieldMetadata::getLabel));
        
        //Hidden fields, plugin UI restriction
        fieldLabelMap.remove(EntityFieldsConstants.FIELD_PHASE);
        fieldLabelMap.remove(EntityFieldsConstants.FIELD_NAME);
        fieldLabelMap.remove(EntityFieldsConstants.FIELD_RANK);
        fieldLabelMap.remove(EntityFieldsConstants.FIELD_ID);

        fieldCombo.clear();
        fieldCombo.addAll(
                fieldLabelMap
                    .keySet()
                    .stream()
                    .sorted()
                    .collect(Collectors.toList())
                );
        
        fieldCombo.setSelection(PluginPreferenceStorage.getShownEntityFields(Entity.getEntityType(entityModel)), false);
    }

}
