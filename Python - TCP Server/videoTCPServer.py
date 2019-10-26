#!/usr/bin/python
import socket
import cv2
import numpy
import base64

# settings for TCP server
TCP_IP = '192.168.0.116'
TCP_PORT = 5001

#encode parameters for image conversion
encode_param=[int(cv2.IMWRITE_JPEG_QUALITY),90]

print("open TCP server socket")
sock = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
sock.setsockopt(socket.SOL_SOCKET, socket.SO_REUSEADDR, 1)
sock.bind((TCP_IP, TCP_PORT))
print("Waiting for TCP client ...")
sock.listen(True)
conn, addr = sock.accept()
print("Connected: " + addr[0]);

# Connect camera
capture = cv2.VideoCapture(0)

# Read first frame from camera
ret, frame = capture.read()
# encode image as base64 String
result, imgencode = cv2.imencode('.jpg', frame, encode_param)
stringData = base64.b64encode(imgencode)

while(True):
	try:
		# read command from client
		input = conn.recv(11)
	except socket.error:
		input = "error"
		print "Lost connection..."
	if input == "getNewFrame":
		# send previous captured frame to server 
		# can also be done in different order: 
		# first capture then send
		conn.send( str(len(stringData)).ljust(16));
		conn.send( stringData );
		# Read next frame from Camera
		ret, frame = capture.read()
		# encode image as base64 String
		result, imgencode = cv2.imencode('.jpg', frame, encode_param)
		stringData = base64.b64encode(imgencode)
	elif input == "closeDriver":
		break
	else:		
		print("Waiting for TCP client ...")
		sock.listen(True)
		conn, addr = sock.accept()
		print("Connected: " + addr[0]); 
	
sock.close()
cv2.destroyAllWindows() 
