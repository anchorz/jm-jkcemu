/*
 * (c) 2010-2017 Jens Mueller
 *
 * Kleincomputer-Emulator
 *
 * Emulation des Joystick-Moduls M008
 */

package jkcemu.emusys.kc85;


public class M008 extends KC85JoystickModule
{
  public M008( int slot )
  {
    super( slot );
  }


	/* --- ueberschriebene Methoden --- */

  @Override
  public String getModuleName()
  {
    return "M008";
  }
}
