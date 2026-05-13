package wfDataModel.service.type;

/**
 * The type of allocation we're looking for. <br>
 * FIXED means a minimum fixed number of servers should be ran (e.g. always have 2 FFA NonRC, 2 TA RC, etc) <br>
 * BALANCED means servers that are purely ran by load balancing (e.g. no minimum count, can have 0 of them running at a given time) <br>
 * ALL means all servers
 * @author MatNova
 *
 */
public enum AllocatorType {
	FIXED,
	BALANCED,
	ALL;
}
