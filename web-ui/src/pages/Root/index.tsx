import React from "react";
import { Provider } from "react-redux";
import store from "../../store";
import GitConfig from "../GitConfig";

const RootPage = () => {
  return (
    <Provider store={store}>
      <GitConfig />
    </Provider>
  );
};

export default RootPage;
