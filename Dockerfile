
# Use the official Tomcat image with Java 11
FROM tomcat:9.0-jdk11

# Clear default Tomcat applications for a clean environment
RUN rm -rf /usr/local/tomcat/webapps/*

# Copy your built .war file to Tomcat
# Change 'dist/YourApp.war' to your actual compiled file path (e.g., target/YourApp.war if using Maven)
COPY target/*.war /usr/local/tomcat/webapps/ROOT.war

# Expose the default Tomcat port
EXPOSE 8080

# Start the Tomcat server
CMD ["catalina.sh", "run"]