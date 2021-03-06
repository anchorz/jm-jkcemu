/*
 * (c) 2008-2017 Jens Mueller
 *
 * Kleincomputer-Emulator
 *
 * Beschreibung eines Zustandes (gedrueckte Tasten) der Tastatur S6009,
 * die nach Brosig/Eisenkolb an den Z1013 angeschlossen ist (12x8-Matrix).
 */

package jkcemu.emusys.z1013;

import java.awt.event.KeyEvent;


public class KeyboardMatrixS6009 extends KeyboardMatrix12x8
{
  private static int myMatrixNormal[] = {
	0x0B,     0x82, '\r', 0x15, 'J', 0,    ';', 0, '7', 'Z', 'B',  'R',
	0x08,     0x83, 0xFF, '\t', 'I', 0,    ']', 0, '6', 'Y', 'A',  'Q',
	0x0A,     0x84, '\\', 0x1B, 'H', 0,    '[', 0, '5', 'X', 0,    'P',
	0x05,     0x85, 0,    0x16, 'G', 0x81, 0,   0, '4', 'W', '/',  'O',
	0x03,     0x88, 0x14, 0x10, 'F', 0,    '=', 0, '3', 'V', '.',  'N',
	0x8E,     0x87, 0x01, 0x12, 'E', 0,    '-', 0, '2', 'U', ',',  'M',
	0x86,     0x89, 0,    0xFF, 'D', 0,    '9', 0, '1', 'T', 0x93, 'L',
	'\u0020', 0x8A, 0,    0x1C, 'C', 0,    '8', 0, '0', 'S', '\'', 'K' };


  private static int myMatrixShift[] = {
	0,    0x97, 0,    0x0C, 'j', 0,    ':', 0, '&', 'z', 'b',  'r',
	0,    0x9C, 0,    0,    'i', 0,    '}', 0, '^', 'y', 'a',  'q',
	0,    0x8C, '|',  0x1C, 'h', 0,    '{', 0, '%', 'x', 0,    'p',
	0x19, 0x95, 0,    0x11, 'g', 0x81, 0,   0, '$', 'w', '?',  'o',
	'~',  0x8D, 0x13, 0x02, 'f', 0,    '+', 0, '#', 'v', '>',  'n',
	0,    0x8F, 0x18, 0x1A, 'e', 0,    '_', 0, '@', 'u', '<',  'm',
	0x8B, 0x90, 0,    0x0F, 'd', 0,    '(', 0, '!', 't', 0,    'l',
	0,    0x99, '`',  0x1F, 'c', 0,    '*', 0, ')', 's', '\"', 'k' };


  public KeyboardMatrixS6009()
  {
    this.matrixNormal = myMatrixNormal;
    this.matrixShift  = myMatrixShift;
    this.ctrlCol      = 7;
    this.ctrlValue    = 0x80;
    this.shiftCol     = 7;
    this.shiftValue   = 0x40;
  }


	/* --- ueberschriebene Methoden --- */

  @Override
  public String getKeyboardType()
  {
    return "12x8_S6009";
  }


  @Override
  public synchronized boolean setKeyCode( int keyCode )
  {
    boolean rv = true;
    reset();
    switch( keyCode ) {
      case KeyEvent.VK_ENTER:
	this.keyboardMatrix[ 2 ] = 0x01;
	break;

      case KeyEvent.VK_LEFT:
	this.keyboardMatrix[ 0 ] = 0x02;
	break;

      case KeyEvent.VK_RIGHT:
	this.keyboardMatrix[ 3 ] = 0x02;
	break;

      case KeyEvent.VK_SPACE:
	this.keyboardMatrix[ 0 ] = 0x80;
	break;

      case KeyEvent.VK_UP:
	this.keyboardMatrix[ 0 ] = 0x01;
	break;

      case KeyEvent.VK_DOWN:
	this.keyboardMatrix[ 0 ] = 0x04;
	break;

      case KeyEvent.VK_BACK_SPACE:
	setKeyCharCode( 8, false );
	break;

      case KeyEvent.VK_TAB:
	setKeyCharCode( '\t', false );
	break;

      case KeyEvent.VK_ESCAPE:
	this.keyboardMatrix[ 3 ] = 0x04;
	break;

      case KeyEvent.VK_DELETE:
	this.keyboardMatrix[ 3 ] = 0x40;
	break;

      default:
	rv = false;
    }
    updShiftKeysPressed();
    return rv;
  }
}
