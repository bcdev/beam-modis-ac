package org.esa.beam.atmosphere.ui;

import org.esa.beam.atmosphere.operator.ModisAtmosCorrectionOp;
import org.esa.beam.framework.gpf.ui.DefaultSingleTargetProductDialog;
import org.esa.beam.framework.ui.command.CommandEvent;
import org.esa.beam.visat.actions.AbstractVisatAction;

/**
 * GLINT Action class
 *
 * @author Olaf Danne
 * @version $Revision: 2703 $ $Date: 2010-01-21 13:51:07 +0100 (Do, 21 Jan 2010) $
 */
public class ModisAtmosphericCorrectionAction extends AbstractVisatAction {

    @Override
    public void actionPerformed(CommandEvent event) {
        final String version = ModisAtmosCorrectionOp.MODIS_ATMOS_CORRECTION_VERSION;
        final String helpId = event.getCommand().getHelpId();
        final DefaultSingleTargetProductDialog productDialog = new DefaultSingleTargetProductDialog(
                "Modis.AtmosCorrection", getAppContext(),
                "MODIS Atmospheric Correction - v" + version, helpId);
        productDialog.setTargetProductNameSuffix("_AC");
        productDialog.show();
    }

}
