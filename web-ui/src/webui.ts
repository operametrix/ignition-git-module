// Central access to @inductiveautomation/ignition-web-ui components.
//
// The package ships strict prop types intended largely for internal/platform use (e.g. DataGrid
// requires paginationParams/setTableQueryParams that have sensible runtime defaults). We re-export
// the components as untyped here and pass the props we need, mirroring the published storybook
// examples, rather than satisfying every required type.
import * as WebUI from "@inductiveautomation/ignition-web-ui";

const ui = WebUI as any;

export const Button = ui.Button;
export const Chip = ui.Chip;
export const DataGrid = ui.DataGrid;
export const Loading = ui.Loading;
export const Modal = ui.Modal;
export const PageHeader = ui.PageHeader;
export const TextArea = ui.TextArea;
