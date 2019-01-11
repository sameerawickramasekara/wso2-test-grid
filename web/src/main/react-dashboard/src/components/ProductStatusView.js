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

import React, {Component} from 'react';
import '../App.css';
import SingleRecord from './SingleRecord.js';
import {add_current_product} from '../actions/testGridActions.js';
import Moment from 'moment'
import {HTTP_OK, HTTP_NOT_FOUND, HTTP_UNAUTHORIZED, LOGIN_URI,
  TESTGRID_CONTEXT, TESTGRID_API_CONTEXT,RED,BLACK,GREEN} from '../constants.js';
import {Button, Table, Modal, ModalHeader, ModalBody, ModalFooter} from 'reactstrap';
import Snackbar from 'material-ui/Snackbar';
const spinningState = "fa fa-spinner fa-pulse";
const defaultState = "fa fa-play-circle"

class ProductStatusView extends Component {

  constructor(props) {
    super(props);
    this.state = {
      showSnackBar:false,
      triggeredIndex:-1,
      hits: [],
      modal: false,
      errorMassage: "",
      snackBarColor: BLACK,
      spinnerState: defaultState
    };

    this.toggle = this.toggle.bind(this);

  }

  handleError(response) {
    if (response.status.toString() === HTTP_UNAUTHORIZED) {
      window.location.replace(LOGIN_URI);
    } else if (!response.ok) {
      throw Error(response.statusText)
    }
    return response;
  }

  toggle(Message) {
    this.setState({
      modal: !this.state.modal,
      errorMassage: Message
    });
  }


  toggleSnackBar(Message,color) {
    console.log("triggered ")
    this.setState({
      snackBarMessage: Message,
      showSnackBar: true,
      snackBarColor: color
    });
  }

  componentDidMount() {
    var url = TESTGRID_API_CONTEXT + '/api/products/product-status';
    fetch(url, {
      method: "GET",
      credentials: 'same-origin',
      headers: {
        'Accept': 'application/json'
      }
    })
      .then(this.handleError)
      .then(response => {
        return response.json()
      })
      .then(data => this.setState({hits: data}))
      .catch(error => console.error(error));
  }

  navigateToRoute(route, product) {
    this.props.dispatch(add_current_product(product));
    this.props.history.push(route);
  }

  triggerBuild(buildName,index) {
    console.log(index)
    this.setState({
      triggeredIndex : index
    })
    // this.state.spinnerState=spinningState
    let url = TESTGRID_API_CONTEXT + '/api/products/trigger-build'
    fetch(url,{
      method:"POST",
      body:buildName,
      credentials:'same-origin'})
    .then(response => {
      if(response.status == HTTP_OK) {
        this.setState({
          triggeredIndex : -1
        })
        let message = "Succesfully triggered the build : " + buildName;
        this.toggleSnackBar(message,GREEN);
        
      } else{
        this.setState({
          triggeredIndex : -1
        })
        let errorMessage = "Unable to trigger the build : " + buildName;
        this.toggleSnackBar(errorMessage,RED);
        
      }
    })
  }

  downloadReport(productName) {
    let url = TESTGRID_API_CONTEXT + '/api/products/reports?product-name=' + productName;
    fetch(url, {
      method: "HEAD",
      credentials: 'same-origin',
    }).then(response => {
        if (response.status === HTTP_NOT_FOUND) {
          let errorMessage = "Unable to locate report in the remote storage, please contact the administrator.";
          this.toggle(errorMessage);
        } else if (response.status !== HTTP_OK) {
          let errorMessage = "Internal server error. Couldn't download the report at the moment, please " +
            "contact the administrator.";
          this.toggle(errorMessage);
        } else if (response.status === HTTP_OK) {
          document.location = url;
        }
      }
    ).catch(error => console.error(error));
  }

  handleSnackbarClose() {
    this.setState({showSnackBar :false})
  }

  render() {
    const products = this.state.hits.map((product, index) => {
      return (<tr key={index}>
        <td><SingleRecord value={product.productStatus}/></td>
        <th onClick={() => this.navigateToRoute(TESTGRID_CONTEXT + "/" + product.productName, {
          productId: product.productId,
          productName: product.productName,
          productStatus: product.productStatus
        })} scope="row  ">
          <i style={{cursor: 'pointer'}}>{product.productName}</i>
        </th>
        <td style={{fontSize: '16px'}}>
          {(() => {
            if (product.lastSuccessTimestamp) {
              return (
                <i onClick={() => this.navigateToRoute(TESTGRID_CONTEXT + "/" +
                                product.productName, {
                                productId: product.productId,
                                productName: product.productName,
                                productStatus: product.productStatus
                })}> {Moment(product.lastSuccessTimestamp).fromNow()}</i>
                )
            } else {
              return ("No Success builds yet!");
            }
          })()}
        </td>
        <td style={{fontSize: '16px'}}>
          {(() => {
            if (product.lastFailureTimestamp) {
              return (
                <i onClick={() => this.navigateToRoute(TESTGRID_CONTEXT + "/" + product.productName, {
                  productId: product.productId,
                  productName: product.productName,
                  productStatus: product.productStatus
                })} style={{cursor: 'pointer'}}>
                  {Moment(product.lastFailureTimestamp).fromNow()}</i>
              );
            } else {
              return ("No failed builds yet!")
            }
          })()}
        </td>
        <td>
          <Button  outline color="info" size="sm" onClick={() => {
                    this.triggerBuild(product.productName,index)
          }}>
          {/* "fa fa-spinner fa-pulse" */}
          <i className= {index === this.state.triggeredIndex ? spinningState : defaultState } aria-hidden="true"> </i>
          </Button>
        </td>
        {/* Note: Commented until the backend coordination is configured.
        <td>
          <Button  outline color="info" size="sm" onClick={() => {
            window.location = '/admin/job/' + product.productName + '/build'
          }}>
            <i className="fa fa-play-circle" aria-hidden="true"> </i>
          </Button>
        </td>
        <td>
          <Button outline color="info" size="sm" onClick={() => {
            window.location = '/admin/job/' + product.productName + '/configure'
          }}>
            <i className="fa fa-cogs" aria-hidden="true"> </i>
          </Button>
        </td>*/}
      </tr>)
    });

    return (
      <div>
        <Snackbar
          open={this.state.showSnackBar}
          message={this.state.snackBarMessage}
          autoHideDuration={4000}
          contentStyle={{
            fontWeight: 600,
            fontSize: "15px"
          }}
          bodyStyle={{
            backgroundColor: this.state.snackBarColor
          }}
          onRequestClose={this.handleSnackbarClose.bind(this)}
        />
        <Table responsive>
          <thead displaySelectAll={false} adjustForCheckbox={false}>
          <tr>
            <th>Status</th>
            <th>Job</th>
            <th>Last Success</th>
            <th>Last Failure</th>
          </tr>
          </thead>
          <tbody displayRowCheckbox={false}>
          {products}
          </tbody>
        </Table>
        <Modal isOpen={this.state.modal} toggle={this.toggle} className={this.props.className} centered={true}>
          <ModalHeader toggle={() => this.toggle("")}>Error</ModalHeader>
          <ModalBody>
            {this.state.errorMassage}
          </ModalBody>
          <ModalFooter>
            <Button color="danger" onClick={() => this.toggle("")}>OK</Button>{' '}
          </ModalFooter>
        </Modal>
      </div>
    )
  }
}

export default ProductStatusView;
