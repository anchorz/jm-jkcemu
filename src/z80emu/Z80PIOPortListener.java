/*
 * (c) 2008-2017 Jens Mueller
 *
 * Z80-Emulator
 *
 * Interface fuer das Empfangen von Ereignissen,
 * wenn sich der IO-Zustand eines PIO-Ports geaendert hat.
 */

package z80emu;


public interface Z80PIOPortListener
{
  public void z80PIOPortStatusChanged(
				Z80PIO          pio,
				Z80PIO.PortInfo port,
				Z80PIO.Status   status );
}

