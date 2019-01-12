package peers;

import java.io.InputStream;
import java.io.DataInputStream;
import java.io.ObjectInput;

/*This class reads the input byte array and transforms it into a message object
 * of the received message type and hands over this to comSupervisor thread
 */
public class InReader extends DataInputStream implements ObjectInput {
	boolean isHandshakeReceived = false;

	public InReader(InputStream in) {
		super(in);
	}

	public Object readObject() {
		try {
			if (!isHandshakeReceived) {
				Handshake handshake = new Handshake();
				if (handshake.msgIsHandShake(this)) {
					isHandshakeReceived = true;
					System.out.println("handshake received successfully");
					return handshake;
				} else {
					System.out.println("handshake is not received properly");
				}

			} else {
				try {
					final int length = readInt(); //the message length is the first 4 bytes
					final int payloadLength = length - 1; // subtract 1byte for the message type
					Byte b = readByte();
					System.out.println("Read byte == "+b);
					Message message = Message.getMessage(payloadLength, Message.getMesByByte(b));
					message.read(this);
					return message;
				} catch (Exception e) {
					e.printStackTrace();
				}
			}
		}

		catch (Exception E) {
			E.printStackTrace();
		}

		return null;
	}
}
