package org.alexbaizeau.MarioKinect;

import org.OpenNI.*;
import java.lang.Math;

import java.nio.ShortBuffer;
import java.util.HashMap;
import java.awt.*;
import java.awt.color.ColorSpace;
import java.awt.event.KeyEvent;
import java.awt.image.*;
import java.awt.Robot;


public class UserTracker extends Component {
	class NewUserObserver implements IObserver<UserEventArgs> {
		@Override
		public void update(IObservable<UserEventArgs> observable,
				UserEventArgs args) {
			System.out.println("New user " + args.getId());
			try {
				if (skeletonCap.needPoseForCalibration()) {
					poseDetectionCap
							.startPoseDetection(calibPose, args.getId());
				} else {
					skeletonCap.requestSkeletonCalibration(args.getId(), true);
				}
			} catch (StatusException e) {
				e.printStackTrace();
			}
		}
	}

	class LostUserObserver implements IObserver<UserEventArgs> {
		@Override
		public void update(IObservable<UserEventArgs> observable,
				UserEventArgs args) {
			System.out.println("Lost user " + args.getId());
			joints.remove(args.getId());
		}
	}

	class CalibrationCompleteObserver implements
			IObserver<CalibrationProgressEventArgs> {
		@Override
		public void update(
				IObservable<CalibrationProgressEventArgs> observable,
				CalibrationProgressEventArgs args) {
			System.out.println("Calibraion complete: " + args.getStatus());
			try {
				if (args.getStatus() == CalibrationProgressStatus.OK) {
					System.out.println("starting tracking " + args.getUser());
					skeletonCap.startTracking(args.getUser());
					joints.put(new Integer(args.getUser()),
							new HashMap<SkeletonJoint, SkeletonJointPosition>());
				} else if (args.getStatus() != CalibrationProgressStatus.MANUAL_ABORT) {
					if (skeletonCap.needPoseForCalibration()) {
						poseDetectionCap.startPoseDetection(calibPose,
								args.getUser());
					} else {
						skeletonCap.requestSkeletonCalibration(args.getUser(),
								true);
					}
				}
			} catch (StatusException e) {
				e.printStackTrace();
			}
		}
	}

	class PoseDetectedObserver implements IObserver<PoseDetectionEventArgs> {
		@Override
		public void update(IObservable<PoseDetectionEventArgs> observable,
				PoseDetectionEventArgs args) {
			System.out.println("Pose " + args.getPose() + " detected for "
					+ args.getUser());
			try {
				poseDetectionCap.stopPoseDetection(args.getUser());
				skeletonCap.requestSkeletonCalibration(args.getUser(), true);
			} catch (StatusException e) {
				e.printStackTrace();
			}
		}
	}

	/**
	 * 
	 */
	private static final long serialVersionUID = 1L;
	private OutArg<ScriptNode> scriptNode;
	private Context context;
	private DepthGenerator depthGen;
	private UserGenerator userGen;
	private SkeletonCapability skeletonCap;
	private PoseDetectionCapability poseDetectionCap;
	String calibPose = null;
	HashMap<Integer, HashMap<SkeletonJoint, SkeletonJointPosition>> joints;

	private boolean drawSkeleton = true;
	private boolean printID = true;
	private boolean printState = true;

	private BufferedImage bimg;
	int width, height;

	private final String SAMPLE_XML_FILE = "/home/alex/workspace/testkinect/config.xml";
	
	private boolean right_pressed = false;
	private boolean left_pressed = false;
	
	private boolean b_pressed = false;
	private boolean y_pressed = false;

	public UserTracker() {

		try {
			scriptNode = new OutArg<ScriptNode>();
			context = Context.createFromXmlFile(SAMPLE_XML_FILE, scriptNode);

			depthGen = DepthGenerator.create(context);
			DepthMetaData depthMD = depthGen.getMetaData();
			
			width = depthMD.getFullXRes();
			height = depthMD.getFullYRes();


			userGen = UserGenerator.create(context);
			skeletonCap = userGen.getSkeletonCapability();
			poseDetectionCap = userGen.getPoseDetectionCapability();

			userGen.getNewUserEvent().addObserver(new NewUserObserver());
			userGen.getLostUserEvent().addObserver(new LostUserObserver());
			skeletonCap.getCalibrationCompleteEvent().addObserver(
					new CalibrationCompleteObserver());
			poseDetectionCap.getPoseDetectedEvent().addObserver(
					new PoseDetectedObserver());

			calibPose = skeletonCap.getSkeletonCalibrationPose();
			joints = new HashMap<Integer, HashMap<SkeletonJoint, SkeletonJointPosition>>();

			skeletonCap.setSkeletonProfile(SkeletonProfile.ALL);

			context.startGeneratingAll();
		} catch (GeneralException e) {
			e.printStackTrace();
			System.exit(1);
		}
	}

	public Dimension getPreferredSize() {
		return new Dimension(width, height);
	}

	Color colors[] = { Color.RED, Color.BLUE, Color.CYAN, Color.GREEN,
			Color.MAGENTA, Color.PINK, Color.YELLOW, Color.WHITE };

	public void getJoint(int user, SkeletonJoint joint) throws StatusException {
		SkeletonJointPosition pos = skeletonCap.getSkeletonJointPosition(user,
				joint);
		if (pos.getPosition().getZ() != 0) {
			joints.get(user).put(
					joint,
					new SkeletonJointPosition(depthGen
							.convertRealWorldToProjective(pos.getPosition()),
							pos.getConfidence()));
		} else {
			joints.get(user).put(joint,
					new SkeletonJointPosition(new Point3D(), 0));
		}
	}

	public void getJoints(int user) throws StatusException {

		getJoint(user, SkeletonJoint.LEFT_ELBOW);
		getJoint(user, SkeletonJoint.LEFT_HAND);

		getJoint(user, SkeletonJoint.RIGHT_ELBOW);
		getJoint(user, SkeletonJoint.RIGHT_HAND);

		getJoint(user, SkeletonJoint.LEFT_KNEE);
		getJoint(user, SkeletonJoint.LEFT_FOOT);

		getJoint(user, SkeletonJoint.RIGHT_KNEE);
		getJoint(user, SkeletonJoint.RIGHT_FOOT);

	}

	void drawLine(Graphics g,
			HashMap<SkeletonJoint, SkeletonJointPosition> jointHash,
			SkeletonJoint joint1, SkeletonJoint joint2) {
		Point3D pos1 = jointHash.get(joint1).getPosition();
		Point3D pos2 = jointHash.get(joint2).getPosition();

		if (jointHash.get(joint1).getConfidence() == 0
				|| jointHash.get(joint2).getConfidence() == 0)
			return;

		g.drawLine((int) pos1.getX(), (int) pos1.getY(), (int) pos2.getX(),
				(int) pos2.getY());
	}

	public void drawSkeleton(Graphics g, int user) throws StatusException,
			AWTException {
		float cos_alpha;
		double alpha;
		float left_hand_x;
		float left_hand_y;
		float right_hand_x;
		float right_hand_y;
		float left_foot_z;
		
		float right_foot_z;
		String message;
		Robot robot_launch = new Robot();
		getJoints(user);
		HashMap<SkeletonJoint, SkeletonJointPosition> dict = joints
				.get(new Integer(user));
	
		drawLine(g, dict, SkeletonJoint.LEFT_ELBOW, SkeletonJoint.LEFT_HAND);
		drawLine(g, dict, SkeletonJoint.RIGHT_ELBOW, SkeletonJoint.RIGHT_HAND);

		right_hand_x = dict.get(SkeletonJoint.RIGHT_HAND).getPosition().getX();
		right_hand_y = dict.get(SkeletonJoint.RIGHT_HAND).getPosition().getY();
		left_hand_x = dict.get(SkeletonJoint.LEFT_HAND).getPosition().getX();
		left_hand_y = dict.get(SkeletonJoint.LEFT_HAND).getPosition().getY();
		left_foot_z = dict.get(SkeletonJoint.LEFT_FOOT).getPosition().getY();
		right_foot_z = dict.get(SkeletonJoint.RIGHT_FOOT).getPosition().getY();

		cos_alpha = (float) ((right_hand_x - left_hand_x) / Math.sqrt(Math.pow(
				(right_hand_x - left_hand_x), 2)
				+ Math.pow((right_hand_y - left_hand_y), 2)));
		alpha = Math.toDegrees(Math.acos(cos_alpha));

		if (left_hand_y > right_hand_y) {

			alpha = alpha * -1;
		}

		message = "Straight";
		
		if (alpha > 20) {

			message = "turn right";
			if (left_pressed){
				robot_launch.keyRelease(KeyEvent.VK_LEFT);
				left_pressed = false;
			}
			if(!right_pressed){
				robot_launch.keyPress(KeyEvent.VK_RIGHT);
				right_pressed = true;
			}
		}
		else if (alpha < -20) {

			message = "turn left";
			if (!left_pressed){
				robot_launch.keyPress(KeyEvent.VK_LEFT);
				left_pressed = true;
			}
			if(right_pressed){
				robot_launch.keyRelease(KeyEvent.VK_RIGHT);
				right_pressed = false;
			}
		}else{
			if(left_pressed){
				robot_launch.keyRelease(KeyEvent.VK_RIGHT);
				right_pressed = false;
			}
			if(right_pressed){
				robot_launch.keyRelease(KeyEvent.VK_LEFT);
				left_pressed = false;
			}

		}
		if (left_foot_z > right_foot_z) {
			if (y_pressed){
				robot_launch.keyRelease(KeyEvent.VK_A);
				y_pressed=false;
			}
			if(!b_pressed){
				robot_launch.keyPress(KeyEvent.VK_Z);
				b_pressed = true;
			}
		}
		if (left_foot_z < right_foot_z) {
			if (!y_pressed){
				robot_launch.keyPress(KeyEvent.VK_A);
				y_pressed=true;
			}
			if(b_pressed){
				robot_launch.keyRelease(KeyEvent.VK_Z);
				b_pressed = false;
			}
		}
		
		//System.out.println("right pressed " + right_pressed);
		//System.out.println("left pressed " + left_pressed);
		//System.out.println("b pressed " + b_pressed);
		//System.out.println("y pressed " + y_pressed);

		drawLine(g, dict, SkeletonJoint.LEFT_KNEE, SkeletonJoint.LEFT_FOOT);
		drawLine(g, dict, SkeletonJoint.RIGHT_KNEE, SkeletonJoint.RIGHT_FOOT);

	}

	public void paint(Graphics g) {
		try {
			int[] users = userGen.getUsers();
			for (int i = 0; i < users.length; ++i) {
				Color c = colors[users[i] % colors.length];
				c = new Color(255 - c.getRed(), 255 - c.getGreen(),
						255 - c.getBlue());

				g.setColor(c);
				if (drawSkeleton && skeletonCap.isSkeletonTracking(users[i])) {
					try {
						drawSkeleton(g, users[i]);
					} catch (AWTException e) {
						// TODO Auto-generated catch block
						e.printStackTrace();
					}
				}

				if (printID) {
					Point3D com = depthGen.convertRealWorldToProjective(userGen
							.getUserCoM(users[i]));
					String label = null;
					if (!printState) {
						label = new String("" + users[i]);
					} else if (skeletonCap.isSkeletonTracking(users[i])) {
						// Tracking
						label = new String(users[i] + " - Tracking");
					} else if (skeletonCap.isSkeletonCalibrating(users[i])) {
						// Calibrating
						label = new String(users[i] + " - Calibrating");
					} else {
						// Nothing
						label = new String(users[i] + " - Looking for pose ("
								+ calibPose + ")");
					}

					g.drawString(label, (int) com.getX(), (int) com.getY());
				}
			}
		} catch (StatusException e) {
			e.printStackTrace();
		}
	}
}
