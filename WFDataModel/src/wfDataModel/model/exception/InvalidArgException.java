package wfDataModel.model.exception;

/**
 * Exception for when invalid args are supplied to a program
 * @author MatNova
 *
 */
public class InvalidArgException extends Exception {

	private static final long serialVersionUID = -568825547041070915L;

	public InvalidArgException(String s) {
		super(s);
	}
	
}
