/*
 * Copyright (c) 2017, WSO2 Inc. (http://www.wso2.org) All Rights Reserved.
 *
 * WSO2 Inc. licenses this file to you under the Apache License,
 * Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package org.wso2.testgrid.web.api;

import org.apache.velocity.exception.ResourceNotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.wso2.testgrid.common.Product;
import org.wso2.testgrid.common.config.ConfigurationContext;
import org.wso2.testgrid.common.config.ConfigurationContext.ConfigurationProperties;
import org.wso2.testgrid.common.exception.TestGridRuntimeException;
import org.wso2.testgrid.common.plugins.AWSArtifactReader;
import org.wso2.testgrid.common.plugins.ArtifactReadable;
import org.wso2.testgrid.common.plugins.ArtifactReaderException;
import org.wso2.testgrid.common.util.StringUtil;
import org.wso2.testgrid.dao.TestGridDAOException;
import org.wso2.testgrid.dao.uow.ProductUOW;
import org.wso2.testgrid.dao.uow.TestPlanUOW;
import org.wso2.testgrid.reporting.AxisColumn;
import org.wso2.testgrid.web.bean.ErrorResponse;
import org.wso2.testgrid.web.bean.ProductStatus;
import org.wso2.testgrid.web.utils.Constants;

import java.io.IOException;
import java.net.URL;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Paths;
import java.security.SecureRandom;
import java.security.cert.X509Certificate;
import java.util.Base64;
import java.util.Locale;
import java.util.Optional;
import java.util.TreeSet;
import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;
import javax.ws.rs.DefaultValue;
import javax.ws.rs.GET;
import javax.ws.rs.HEAD;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

/**
 * REST service implementation of Products.
 */

@Path("/products")
@Produces(MediaType.APPLICATION_JSON)
public class ProductService {

    private static final Logger logger = LoggerFactory.getLogger(ProductService.class);

    /**
     * This has the implementation of the REST API for fetching all the Products.
     *
     * @return A list of available Products.
     */
    @GET
    public Response getAllProducts() {
        try {
            ProductUOW productUOW = new ProductUOW();
            return Response.status(Response.Status.OK).entity(APIUtil.getProductBeans(productUOW.getProducts())).
                    build();
        } catch (TestGridDAOException e) {
            String msg = "Error occurred while fetching the Products.";
            logger.error(msg, e);
            return Response.serverError().entity(
                    new ErrorResponse.ErrorResponseBuilder().setMessage(msg).build()).build();
        }
    }

    /**
     * This has the implementation of the REST API for fetching a Product by id.
     *
     * @return the matching Product.
     */
    @GET
    @Path("/{id}")
    public Response getProduct(@PathParam("id") String id) {
        try {
            ProductUOW productUOW = new ProductUOW();
            Optional<Product> product = productUOW.getProduct(id);
            if (product.isPresent()) {
                return Response.status(Response.Status.OK).entity(APIUtil.getProductBean(product.get())).build();
            } else {
                return Response.status(Response.Status.NOT_FOUND).entity(new ErrorResponse.ErrorResponseBuilder().
                        setMessage("Unable to find the requested Product by id : '" + id + "'").build()).
                        build();
            }
        } catch (TestGridDAOException e) {
            String msg = "Error occurred while fetching the Product by id : '" + id + "'";
            logger.error(msg, e);
            return Response.serverError().entity(
                    new ErrorResponse.ErrorResponseBuilder().setMessage(msg).build()).build();
        }
    }

    /**
     * This method returns the list of products that are currently in TestGrid.
     * <p>
     * <p> The products are returned as a json response with the last build information and
     * the last failed build information<p/>
     *
     * @return list of products
     */
    @GET
    @Path("/product-status")
    public Response getAllProductStatuses() {
        TestPlanUOW testPlanUOW = new TestPlanUOW();
        ProductUOW productUOW = new ProductUOW();
        TreeSet<ProductStatus> list = new TreeSet<>();
        try {
            for (Product product : productUOW.getProducts()) {
                ProductStatus status = new ProductStatus(product.getId(), product.getName(),
                        testPlanUOW.getCurrentStatus(product).toString());
                status.setLastSuccessTimestamp(product.getLastSuccessTimestamp());
                status.setLastFailureTimestamp(product.getLastFailureTimestamp());
                list.add(status);
            }
        } catch (TestGridDAOException e) {
            String msg = "Error occurred while fetching the Product statuses ";
            logger.error(msg, e);
            return Response.serverError().entity(
                    new ErrorResponse.ErrorResponseBuilder().setMessage(msg).build()).build();
        }
        return Response.status(Response.Status.OK).entity(list).build();
    }

    /**
     * This method returns the product if the requested product name exists in the TestGrid.
     *
     * <p> The product is returned as a json response with the last build information and
     * the last failed build information<p/>
     *
     * @return product
     */
    @GET
    @Path("/product-status/{productName}")
    public Response getProductStatus(
            @PathParam("productName") String productName) {
        try {
            TestPlanUOW testPlanUOW = new TestPlanUOW();
            ProductUOW productUOW = new ProductUOW();
            Optional<Product> productInstance = productUOW.getProduct(productName);
            Product product;
            if (productInstance.isPresent()) {
                product = productInstance.get();
                ProductStatus productStatus = new ProductStatus(product.getId(), product.getName(),
                        testPlanUOW.getCurrentStatus(product).toString());
                productStatus.setLastSuccessTimestamp(product.getLastSuccessTimestamp());
                productStatus.setLastFailureTimestamp(product.getLastFailureTimestamp());
                return Response.status(Response.Status.OK).entity(productStatus).build();
            } else {
                String msg = "Could not found the product:" + productName + " in TestGrid. Please check the "
                        + "infrastructure_parameter table";
                logger.error(msg);
                return Response.status(Response.Status.NOT_FOUND).entity(msg).build();
            }
        } catch (TestGridDAOException e) {
            String msg = "Error occurred while fetching the statuses of the product: " + productName + ". Please "
                    + "check the database configurations";
            logger.error(msg, e);
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR).entity(msg).build();
        }
    }

    /**
     * This method verifies the existence of the requesting report in the remote location.
     * <p>
     *
     * @return existence of the querying product report
     */
    @HEAD
    @Path("/reports")
    public Response isProductReportExist(
            @QueryParam("product-name") String productName,
            @DefaultValue("false") @QueryParam("show-success") Boolean showSuccess,
            @DefaultValue("SCENARIO") @QueryParam("group-by") String groupBy) {
        try {
            ProductUOW productUOW = new ProductUOW();
            Optional<Product> productInstance = productUOW.getProduct(productName);
            Product product;
            if (productInstance.isPresent()) {
                product = productInstance.get();
                AxisColumn uniqueAxisColumn = AxisColumn.valueOf(groupBy.toUpperCase(Locale.ENGLISH));
                String fileName = StringUtil
                        .concatStrings(product.getName(), "-", uniqueAxisColumn, Constants.HTML_EXTENSION);
                String bucketKey = Paths.get(Constants.AWS_BUCKET_ARTIFACT_DIR, productName, fileName).toString();
                ArtifactReadable artifactReadable = new AWSArtifactReader(ConfigurationContext.
                        getProperty(ConfigurationContext.ConfigurationProperties.AWS_REGION_NAME), ConfigurationContext.
                        getProperty(ConfigurationContext.ConfigurationProperties.AWS_S3_BUCKET_NAME));
                if (artifactReadable.isArtifactExist(bucketKey)) {
                    return Response.status(Response.Status.OK).entity("The artifact exists in the remote storage")
                            .build();
                }
                return Response.status(Response.Status.NOT_FOUND)
                        .entity("Couldn't found the Artifact in the remote location").build();
            }
            return Response.status(Response.Status.NOT_FOUND).entity("Could't found the Product in Test Grid").build();
        } catch (TestGridDAOException e) {
            String msg = "Error occurred while fetching the product for product name : '" + productName + "' ";
            logger.error(msg, e);
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR).entity(msg).build();
        } catch (ArtifactReaderException e) {
            String msg = "Error occurred while creating AWS artifact reader.";
            logger.error(msg, e);
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR).entity(msg).build();
        } catch (IOException e) {
            String msg = "Error occurred while accessing configurations.";
            logger.error(msg, e);
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR).entity(msg).build();
        }
    }

    /**
     * This method is able to get the latest report of a given product from the remote storage and return.
     * <p>
     * <p> The report is returned as a html file<p/>
     *
     * @return latest report of querying product
     */
    @GET
    @Path("/reports")
    @Produces(MediaType.APPLICATION_OCTET_STREAM)
    public Response getProductReport(
            @QueryParam("product-name") String productName,
            @DefaultValue("false") @QueryParam("show-success") Boolean showSuccess,
            @DefaultValue("SCENARIO") @QueryParam("group-by") String groupBy) {
        try {
            ProductUOW productUOW = new ProductUOW();
            Optional<Product> productInstance = productUOW.getProduct(productName);
            Product product;
            if (productInstance.isPresent()) {
                product = productInstance.get();
                AxisColumn uniqueAxisColumn = AxisColumn.valueOf(groupBy.toUpperCase(Locale.ENGLISH));
                String fileName = StringUtil
                        .concatStrings(product.getName(), "-", uniqueAxisColumn, Constants.HTML_EXTENSION);
                String bucketKey = Paths.get(Constants.AWS_BUCKET_ARTIFACT_DIR, productName, fileName).toString();
                ArtifactReadable artifactReadable = new AWSArtifactReader(ConfigurationContext.
                        getProperty(ConfigurationContext.ConfigurationProperties.AWS_REGION_NAME), ConfigurationContext.
                        getProperty(ConfigurationContext.ConfigurationProperties.AWS_S3_BUCKET_NAME));
                Response.ResponseBuilder response = Response
                        .ok(artifactReadable.getArtifactStream(bucketKey), MediaType.APPLICATION_OCTET_STREAM);
                response.status(Response.Status.OK);
                response.type("application/html");
                response.header("Content-Disposition", "attachment; filename=\"" + fileName + "\"");
                return response.build();
            }
            return Response.status(Response.Status.NOT_FOUND).entity("Product not found").build();

        } catch (TestGridDAOException e) {
            String msg = "Error occurred while fetching the product for product name : '" + productName + "' ";
            logger.error(msg, e);
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR).entity(msg).build();
        } catch (ArtifactReaderException e) {
            String msg = "Error occurred while creating AWS artifact reader.";
            logger.error(msg, e);
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR).entity(msg).build();
        } catch (IOException e) {
            String msg = "Error occurred while accessing configurations.";
            logger.error(msg, e);
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR).entity(msg).build();
        } catch (ResourceNotFoundException e) {
            String msg = "Error occurred while getting the report.";
            logger.error(msg, e);
            return Response.status(Response.Status.NOT_FOUND).entity(msg).build();
        } catch (TestGridRuntimeException e) {
            String msg = "Error occurred while accessing the remote storage";
            logger.error(msg, e);
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR).entity(msg).build();
        }
    }

    /**
     * This api endpoint recieves a product name and then triggers a Jenkins build in the
     * relevent environment
     *
     * @param jobName the jobName
     * @return status of the job triggering process
     */
    @POST
    @Path("/trigger-build")
    public Response triggerBuildforProduct(String jobName) {
        HttpsURLConnection connection = null;
        String jenkinsUser = ConfigurationContext
                .getProperty(ConfigurationProperties.JENKINS_USER);
        String jenkinsToken = ConfigurationContext
                .getProperty(ConfigurationProperties.JENKINS_TOKEN);
        String jenkinsHost = ConfigurationContext
                .getProperty(ConfigurationProperties.JENKINS_HOST);
        String jobToken = ConfigurationContext
                .getProperty(ConfigurationProperties.JENKINS_BUILD_TOKEN);
        logger.error("Running tests for the job : " + jobName);
        logger.error("Jenkins host " + jenkinsHost);

        try {
            String authorizationHeader = "Basic " + new String(Base64.getEncoder().
                    encode((StringUtil.concatStrings(jenkinsUser, ":", jenkinsToken))
                            .getBytes(StandardCharsets.UTF_8)), Charset.defaultCharset());
            URL buildTriggerUrl = new URL(jenkinsHost + "/job/" + jobName + "/build?token=" +
                    jobToken);
            logger.error("Build URL " + buildTriggerUrl.toString());
            connection = (HttpsURLConnection) buildTriggerUrl.openConnection();
            connection.setRequestProperty("Authorization", authorizationHeader);
            connection.setRequestMethod("GET");
            connection.setDoOutput(true);
            SSLSocketFactory sslSocketFactory = createSslSocketFactory();
            connection.setSSLSocketFactory(sslSocketFactory);

            int responseCode = connection.getResponseCode();
            if (responseCode == Response.Status.CREATED.getStatusCode()) {
                Response.ResponseBuilder response = Response.ok("Successfully triggered the job");
                response.status(Response.Status.OK);
                return response.build();
            } else {
                String msg = "Error occurred while triggering the build : " + jobName +
                        " with response code : " + responseCode;
                logger.error(msg);
                return Response.status(Response.Status.INTERNAL_SERVER_ERROR).entity(msg).build();
            }

        } catch (IOException e) {
            String msg = "Error occurred while opening the connection to the build : " + jobName;
            logger.error(msg, e);
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR).entity(msg).build();

        } catch (Exception e) {
            String msg = "Error occurred while triggering the build : " + jobName;
            logger.error(msg, e);
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR).entity(msg).build();
        }
    }

    /**
     * This method is to bypass SSL verification
     *
     * @return SSL socket factory that by will bypass SSL verification
     * @throws Exception java.security exception is thrown in an issue with SSLContext
     */
    private static SSLSocketFactory createSslSocketFactory() throws Exception {

        TrustManager[] byPassTrustManagers = new TrustManager[]{new X509TrustManager() {
            public X509Certificate[] getAcceptedIssuers() {

                return new X509Certificate[0];
            }

            public void checkClientTrusted(X509Certificate[] chain, String authType) {

            }

            public void checkServerTrusted(X509Certificate[] chain, String authType) {

            }
        }};
        SSLContext sslContext = SSLContext.getInstance("TLS");
        sslContext.init(null, byPassTrustManagers, new SecureRandom());
        return sslContext.getSocketFactory();
    }
}
