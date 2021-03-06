/*
 * (c) 2011-2020 Jens Mueller
 *
 * Kleincomputer-Emulator
 *
 * Komponente fuer die Einstellungen zweier RAM-Floppies
 */

package jkcemu.settings;

import java.awt.GridBagLayout;
import java.awt.GridBagConstraints;
import java.awt.Insets;
import java.util.Properties;
import javax.swing.JSeparator;
import jkcemu.base.EmuSys;
import jkcemu.base.GUIFactory;
import jkcemu.base.RAMFloppy;
import jkcemu.base.UserInputException;


public class RAMFloppiesSettingsFld extends AbstractSettingsFld
{
  private RAMFloppySettingsFld rf1Fld;
  private RAMFloppySettingsFld rf2Fld;


  public RAMFloppiesSettingsFld(
			SettingsFrm      settingsFrm,
			String           propPrefix,
			String           labelText1,
			RAMFloppy.RFType rfType1,
			String           labelText2,
			RAMFloppy.RFType rfType2 )
  {
    super( settingsFrm, propPrefix );

    setLayout( new GridBagLayout() );

    GridBagConstraints gbc = new GridBagConstraints(
					0, 0,
					1, 1,
					1.0, 0.0,
					GridBagConstraints.WEST,
					GridBagConstraints.HORIZONTAL,
					new Insets( 0, 0, 5, 0 ),
					0, 0 );

    this.rf1Fld = new RAMFloppySettingsFld(
				settingsFrm,
				propPrefix + EmuSys.PROP_RF1_PREFIX,
				labelText1,
				rfType1 );
    add( this.rf1Fld, gbc );

    gbc.insets.top   = 5;
    gbc.insets.left  = 5;
    gbc.insets.right = 5;
    gbc.gridy++;
    add( GUIFactory.createSeparator(), gbc );

    this.rf2Fld = new RAMFloppySettingsFld(
				settingsFrm,
				propPrefix + EmuSys.PROP_RF2_PREFIX,
				labelText2,
				rfType2 );
    gbc.insets.left   = 0;
    gbc.insets.right  = 0;
    gbc.insets.bottom = 0;
    gbc.gridy++;
    add( this.rf2Fld, gbc );
  }


	/* --- ueberschriebene Methoden --- */

  @Override
  public void applyInput(
		Properties props,
		boolean    selected ) throws UserInputException
  {
    this.rf1Fld.applyInput( props, selected );
    this.rf2Fld.applyInput( props, selected );
  }


  @Override
  public void setEnabled( boolean state )
  {
    super.setEnabled( state );
    this.rf1Fld.setEnabled( state );
    this.rf2Fld.setEnabled( state );
  }


  @Override
  public void updFields( Properties props )
  {
    this.rf1Fld.updFields( props );
    this.rf2Fld.updFields( props );
  }
}
