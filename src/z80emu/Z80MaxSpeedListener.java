/*
 * (c) 2008-2017 Jens Mueller
 *
 * Z80-Emulator
 *
 * Interface fuer das Empfangen von Ereignissen,
 * wenn die maximale Emulationsgeschwindigkeit geaendert wird.
 */

package z80emu;


public interface Z80MaxSpeedListener
{
  public void z80MaxSpeedChanged( Z80CPU cpu );
}
