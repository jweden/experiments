#This was used with perl 5.10 which has an updated threading model.
#
# This program sends a desired number of concurrent http GET requests
# and looks for the desired response within a desired response time.
#
# Run this program with four arguments, for example as:
#  perl ./concurrent.pl 5 http://www.google.com 4 '200 OK'
#
# FIRST ARGUMENT: the number of concurrent requests
# SECOND ARGUMENT: the url to which to send http requests 
# THIRD ARGUMENT: how long to wait for ALL the threads to finish
# FOURTH ARGUMENT: the response string each http request will look for 

use threads;
use strict;
use warnings;

require LWP;
require LWP::UserAgent;
require LWP::Debug;

package RequestAgent;
my @ISA = qw(LWP::UserAgent);

my $ua = new LWP::UserAgent;
my $netloc= $ARGV[1];

# Next, concurrently send the http requests and parse the responses
for (1..$ARGV[0]) {
	my $test = threads->create(\&mytest);
}

sleep $ARGV[2];

#Subroutine that sends the http requests.  This is called concurrently.
sub mytest {
	# Send the request and get a response back from the server
	my $request = HTTP::Request->new(GET => $netloc);

	my $begin_time = time();
    	my $response = $ua->request($request);
      $ua->redirect_ok($request, $response); #allow redirect
      my $fullResponse = $response->as_string;

	my $finish_time = time - $begin_time;
	if ($fullResponse =~ /$ARGV[3]/) {
               print "Found response containing the string \"$ARGV[3]\" in $finish_time seconds\n";
        } else {
	       print "WARNING: did not find response containing the string \"$ARGV[3]\"\n";	
	}
}

