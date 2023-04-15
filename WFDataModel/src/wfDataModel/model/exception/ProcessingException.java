package wfDataModel.model.exception;

/**
 * Exception for when some kind of processing issue has occurred
 * @author MatNova
 *
 */
public class ProcessingException extends Exception {

	private static final long serialVersionUID = 771338856276303572L;

	public ProcessingException(String s) {
		super(s);
	}
}
