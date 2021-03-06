/*
 * (c) 2008-2017 Jens Mueller
 *
 * Z80-Emulator
 *
 * Interface fuer das Empfangen von Ereignissen,
 * ob die CPU in den HALT-Zustand gegangen ist oder umgekehrt
 */

package z80emu;


public interface Z80HaltStateListener
{
  public void z80HaltStateChanged( Z80CPU cpu, boolean haltState );
}

