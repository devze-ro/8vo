#include <oleacc.h>

struct Lectern0Accessibility
{
  IAccessible iface;
  volatile LONG reference_count;
  HWND window;
  Lectern0App *app;
  U64 semantic_hash;
  UI0ID focused_id;
};

FUNCTION Lectern0Accessibility *
lectern0_accessibility_from_iface(IAccessible *iface)
{
  return CONTAINING_RECORD(iface, Lectern0Accessibility, iface);
}

FUNCTION const ReaderViewSemanticNode *
lectern0_accessibility_node(const Lectern0Accessibility *accessibility,
                            VARIANT child,
                            long *out_child_id)
{
  if (!accessibility || !accessibility->app || child.vt != VT_I4 ||
      child.lVal <= CHILDID_SELF)
    return 0;
  const ReaderViewFrame *frame = &accessibility->app->reader_view_frame;
  long index = child.lVal - 1;
  if (!frame->semantic_nodes || index < 0 || index >= frame->semantic_node_count)
    return 0;
  if (out_child_id) *out_child_id = child.lVal;
  return frame->semantic_nodes + index;
}

FUNCTION BSTR
lectern0_accessibility_bstr(ReaderViewText text)
{
  if (!text.data || text.size <= 0) return SysAllocStringLen(L"", 0);
  int count = MultiByteToWideChar(CP_UTF8, MB_ERR_INVALID_CHARS,
                                  text.data, text.size, 0, 0);
  if (count <= 0)
    count = MultiByteToWideChar(CP_UTF8, 0, text.data, text.size, 0, 0);
  if (count <= 0) return 0;
  BSTR result = SysAllocStringLen(0, (UINT)count);
  if (!result) return 0;
  if (MultiByteToWideChar(CP_UTF8, 0, text.data, text.size,
                          result, count) != count)
  {
    SysFreeString(result);
    return 0;
  }
  return result;
}

FUNCTION long
lectern0_accessibility_role(ReaderViewSemanticRole role)
{
  switch (role)
  {
    case ReaderViewSemantic_Toolbar: return ROLE_SYSTEM_TOOLBAR;
    case ReaderViewSemantic_Panel: return ROLE_SYSTEM_PANE;
    case ReaderViewSemantic_Dialog: return ROLE_SYSTEM_DIALOG;
    case ReaderViewSemantic_Group: return ROLE_SYSTEM_GROUPING;
    case ReaderViewSemantic_Button: return ROLE_SYSTEM_PUSHBUTTON;
    case ReaderViewSemantic_ToggleButton: return ROLE_SYSTEM_CHECKBUTTON;
    case ReaderViewSemantic_SearchBox: return ROLE_SYSTEM_TEXT;
    case ReaderViewSemantic_TextArea: return ROLE_SYSTEM_TEXT;
    case ReaderViewSemantic_Slider: return ROLE_SYSTEM_SLIDER;
    case ReaderViewSemantic_Tab: return ROLE_SYSTEM_PAGETAB;
    case ReaderViewSemantic_List: return ROLE_SYSTEM_LIST;
    case ReaderViewSemantic_ListItem: return ROLE_SYSTEM_LISTITEM;
    case ReaderViewSemantic_Menu: return ROLE_SYSTEM_MENUPOPUP;
    case ReaderViewSemantic_MenuItem: return ROLE_SYSTEM_MENUITEM;
    case ReaderViewSemantic_Status: return ROLE_SYSTEM_STATUSBAR;
    default: return ROLE_SYSTEM_CLIENT;
  }
}

FUNCTION long
lectern0_accessibility_state(const ReaderViewSemanticNode *node)
{
  if (!node) return 0;
  long result = 0;
  if (!(node->flags & ReaderViewSemantic_Enabled)) result |= STATE_SYSTEM_UNAVAILABLE;
  if (node->flags & ReaderViewSemantic_Focusable) result |= STATE_SYSTEM_FOCUSABLE;
  if (node->flags & ReaderViewSemantic_Focused) result |= STATE_SYSTEM_FOCUSED;
  if (node->flags & ReaderViewSemantic_Selected) result |= STATE_SYSTEM_SELECTED;
  if (node->flags & ReaderViewSemantic_Checked) result |= STATE_SYSTEM_CHECKED;
  if (node->flags & ReaderViewSemantic_Expanded) result |= STATE_SYSTEM_EXPANDED;
  if (node->flags & ReaderViewSemantic_Busy) result |= STATE_SYSTEM_BUSY;
  if (node->flags & ReaderViewSemantic_Offscreen) result |= STATE_SYSTEM_OFFSCREEN;
  if (node->flags & ReaderViewSemantic_ReadOnly) result |= STATE_SYSTEM_READONLY;
  return result;
}

FUNCTION HRESULT STDMETHODCALLTYPE
lectern0_accessibility_query_interface(IAccessible *iface,
                                       REFIID riid,
                                       void **out_object)
{
  if (!out_object) return E_POINTER;
  *out_object = 0;
  if (!IsEqualIID(riid, &IID_IUnknown) &&
      !IsEqualIID(riid, &IID_IDispatch) &&
      !IsEqualIID(riid, &IID_IAccessible))
    return E_NOINTERFACE;
  *out_object = iface;
  (void)InterlockedIncrement(
    &lectern0_accessibility_from_iface(iface)->reference_count);
  return S_OK;
}

FUNCTION ULONG STDMETHODCALLTYPE
lectern0_accessibility_add_ref(IAccessible *iface)
{
  return (ULONG)InterlockedIncrement(
    &lectern0_accessibility_from_iface(iface)->reference_count);
}

FUNCTION ULONG STDMETHODCALLTYPE
lectern0_accessibility_release(IAccessible *iface)
{
  Lectern0Accessibility *accessibility = lectern0_accessibility_from_iface(iface);
  LONG remaining = InterlockedDecrement(&accessibility->reference_count);
  if (remaining == 0) free(accessibility);
  return (ULONG)remaining;
}

FUNCTION HRESULT STDMETHODCALLTYPE
lectern0_accessibility_get_type_info_count(IAccessible *iface, UINT *out_count)
{
  (void)iface;
  if (!out_count) return E_POINTER;
  *out_count = 0;
  return S_OK;
}

FUNCTION HRESULT STDMETHODCALLTYPE
lectern0_accessibility_get_type_info(IAccessible *iface, UINT index,
                                     LCID locale, ITypeInfo **out_info)
{
  (void)iface; (void)index; (void)locale;
  if (out_info) *out_info = 0;
  return E_NOTIMPL;
}

FUNCTION HRESULT STDMETHODCALLTYPE
lectern0_accessibility_get_ids_of_names(IAccessible *iface, REFIID riid,
                                        LPOLESTR *names, UINT name_count,
                                        LCID locale, DISPID *out_ids)
{
  (void)iface; (void)riid; (void)names; (void)name_count;
  (void)locale; (void)out_ids;
  return DISP_E_UNKNOWNNAME;
}

FUNCTION HRESULT STDMETHODCALLTYPE
lectern0_accessibility_invoke(IAccessible *iface, DISPID member, REFIID riid,
                              LCID locale, WORD flags, DISPPARAMS *params,
                              VARIANT *out_result, EXCEPINFO *out_exception,
                              UINT *out_argument_error)
{
  (void)iface; (void)member; (void)riid; (void)locale; (void)flags;
  (void)params; (void)out_result; (void)out_exception;
  (void)out_argument_error;
  return DISP_E_MEMBERNOTFOUND;
}

FUNCTION HRESULT STDMETHODCALLTYPE
lectern0_accessibility_get_parent(IAccessible *iface, IDispatch **out_parent)
{
  if (!out_parent) return E_POINTER;
  *out_parent = 0;
  Lectern0Accessibility *accessibility = lectern0_accessibility_from_iface(iface);
  IAccessible *parent = 0;
  HRESULT result = AccessibleObjectFromWindow(accessibility->window,
                                               OBJID_WINDOW,
                                               &IID_IAccessible,
                                               (void **)&parent);
  if (SUCCEEDED(result)) *out_parent = (IDispatch *)parent;
  return result;
}

FUNCTION HRESULT STDMETHODCALLTYPE
lectern0_accessibility_get_child_count(IAccessible *iface, long *out_count)
{
  if (!out_count) return E_POINTER;
  Lectern0Accessibility *accessibility = lectern0_accessibility_from_iface(iface);
  *out_count = accessibility->app ?
    accessibility->app->reader_view_frame.semantic_node_count : 0;
  return S_OK;
}

FUNCTION HRESULT STDMETHODCALLTYPE
lectern0_accessibility_get_child(IAccessible *iface, VARIANT child,
                                 IDispatch **out_child)
{
  if (!out_child) return E_POINTER;
  *out_child = 0;
  return lectern0_accessibility_node(
    lectern0_accessibility_from_iface(iface), child, 0) ? S_FALSE : E_INVALIDARG;
}

FUNCTION HRESULT STDMETHODCALLTYPE
lectern0_accessibility_get_name(IAccessible *iface, VARIANT child,
                                BSTR *out_name)
{
  if (!out_name) return E_POINTER;
  *out_name = 0;
  if (child.vt == VT_I4 && child.lVal == CHILDID_SELF)
  {
    *out_name = SysAllocString(L"lectern0 EPUB reader");
    return *out_name ? S_OK : E_OUTOFMEMORY;
  }
  const ReaderViewSemanticNode *node = lectern0_accessibility_node(
    lectern0_accessibility_from_iface(iface), child, 0);
  if (!node) return E_INVALIDARG;
  *out_name = lectern0_accessibility_bstr(node->name);
  return *out_name ? S_OK : E_OUTOFMEMORY;
}

FUNCTION HRESULT STDMETHODCALLTYPE
lectern0_accessibility_get_value(IAccessible *iface, VARIANT child,
                                 BSTR *out_value)
{
  if (!out_value) return E_POINTER;
  *out_value = 0;
  const ReaderViewSemanticNode *node = lectern0_accessibility_node(
    lectern0_accessibility_from_iface(iface), child, 0);
  if (!node) return child.vt == VT_I4 && child.lVal == CHILDID_SELF ? S_FALSE : E_INVALIDARG;
  if (node->value.data && node->value.size > 0)
  {
    *out_value = lectern0_accessibility_bstr(node->value);
  }
  else if (node->role == ReaderViewSemantic_Slider)
  {
    wchar_t value[64] = {0};
    (void)_snwprintf_s(value, ARRAY_COUNT(value), _TRUNCATE,
                       L"%llu of %llu",
                       (unsigned long long)node->range_value,
                       (unsigned long long)node->range_max);
    *out_value = SysAllocString(value);
  }
  return *out_value ? S_OK : S_FALSE;
}

FUNCTION HRESULT STDMETHODCALLTYPE
lectern0_accessibility_no_text(IAccessible *iface, VARIANT child,
                               BSTR *out_text)
{
  (void)iface; (void)child;
  if (!out_text) return E_POINTER;
  *out_text = 0;
  return S_FALSE;
}

FUNCTION HRESULT STDMETHODCALLTYPE
lectern0_accessibility_get_role(IAccessible *iface, VARIANT child,
                                VARIANT *out_role)
{
  if (!out_role) return E_POINTER;
  VariantInit(out_role);
  out_role->vt = VT_I4;
  if (child.vt == VT_I4 && child.lVal == CHILDID_SELF)
  {
    out_role->lVal = ROLE_SYSTEM_CLIENT;
    return S_OK;
  }
  const ReaderViewSemanticNode *node = lectern0_accessibility_node(
    lectern0_accessibility_from_iface(iface), child, 0);
  if (!node) return E_INVALIDARG;
  out_role->lVal = lectern0_accessibility_role(node->role);
  return S_OK;
}

FUNCTION HRESULT STDMETHODCALLTYPE
lectern0_accessibility_get_state(IAccessible *iface, VARIANT child,
                                 VARIANT *out_state)
{
  if (!out_state) return E_POINTER;
  VariantInit(out_state);
  out_state->vt = VT_I4;
  if (child.vt == VT_I4 && child.lVal == CHILDID_SELF) return S_OK;
  const ReaderViewSemanticNode *node = lectern0_accessibility_node(
    lectern0_accessibility_from_iface(iface), child, 0);
  if (!node) return E_INVALIDARG;
  out_state->lVal = lectern0_accessibility_state(node);
  return S_OK;
}

FUNCTION HRESULT STDMETHODCALLTYPE
lectern0_accessibility_get_help_topic(IAccessible *iface, BSTR *out_file,
                                      VARIANT child, long *out_topic)
{
  (void)iface; (void)child;
  if (!out_file || !out_topic) return E_POINTER;
  *out_file = 0;
  *out_topic = 0;
  return S_FALSE;
}

FUNCTION HRESULT STDMETHODCALLTYPE
lectern0_accessibility_get_focus(IAccessible *iface, VARIANT *out_child)
{
  if (!out_child) return E_POINTER;
  VariantInit(out_child);
  Lectern0Accessibility *accessibility = lectern0_accessibility_from_iface(iface);
  if (!accessibility->app) return S_FALSE;
  const ReaderViewFrame *frame = &accessibility->app->reader_view_frame;
  for (UI0S32 index = 0; index < frame->semantic_node_count; index += 1)
  {
    if (frame->semantic_nodes[index].flags & ReaderViewSemantic_Focused)
    {
      out_child->vt = VT_I4;
      out_child->lVal = index + 1;
      return S_OK;
    }
  }
  return S_FALSE;
}

FUNCTION HRESULT STDMETHODCALLTYPE
lectern0_accessibility_get_selection(IAccessible *iface, VARIANT *out_child)
{
  if (!out_child) return E_POINTER;
  VariantInit(out_child);
  Lectern0Accessibility *accessibility = lectern0_accessibility_from_iface(iface);
  if (!accessibility->app) return S_FALSE;
  const ReaderViewFrame *frame = &accessibility->app->reader_view_frame;
  for (UI0S32 index = 0; index < frame->semantic_node_count; index += 1)
  {
    if (frame->semantic_nodes[index].flags & ReaderViewSemantic_Selected)
    {
      out_child->vt = VT_I4;
      out_child->lVal = index + 1;
      return S_OK;
    }
  }
  return S_FALSE;
}

FUNCTION HRESULT STDMETHODCALLTYPE
lectern0_accessibility_get_default_action(IAccessible *iface, VARIANT child,
                                          BSTR *out_action)
{
  if (!out_action) return E_POINTER;
  *out_action = 0;
  const ReaderViewSemanticNode *node = lectern0_accessibility_node(
    lectern0_accessibility_from_iface(iface), child, 0);
  if (!node || !(node->flags & ReaderViewSemantic_Focusable))
    return node ? S_FALSE : E_INVALIDARG;
  const wchar_t *label = L"Activate";
  if (node->role == ReaderViewSemantic_SearchBox ||
      node->role == ReaderViewSemantic_TextArea) label = L"Edit";
  else if (node->role == ReaderViewSemantic_Slider) label = L"Focus";
  *out_action = SysAllocString(label);
  return *out_action ? S_OK : E_OUTOFMEMORY;
}

FUNCTION HRESULT STDMETHODCALLTYPE
lectern0_accessibility_select(IAccessible *iface, long flags, VARIANT child)
{
  Lectern0Accessibility *accessibility = lectern0_accessibility_from_iface(iface);
  const ReaderViewSemanticNode *node = lectern0_accessibility_node(
    accessibility, child, 0);
  if (!node || !(node->flags & ReaderViewSemantic_Enabled) ||
      !(node->flags & ReaderViewSemantic_Focusable)) return E_INVALIDARG;
  if (flags & (SELFLAG_TAKEFOCUS | SELFLAG_TAKESELECTION))
  {
    (void)SetFocus(accessibility->window);
    if (!reader_view_accessibility_focus(&accessibility->app->reader_view_state,
                                         node->id))
      return E_FAIL;
    (void)InvalidateRect(accessibility->window, 0, FALSE);
    return S_OK;
  }
  return S_FALSE;
}

FUNCTION HRESULT STDMETHODCALLTYPE
lectern0_accessibility_location(IAccessible *iface,
                                long *out_left, long *out_top,
                                long *out_width, long *out_height,
                                VARIANT child)
{
  if (!out_left || !out_top || !out_width || !out_height) return E_POINTER;
  Lectern0Accessibility *accessibility = lectern0_accessibility_from_iface(iface);
  RECT rect = {0};
  if (child.vt == VT_I4 && child.lVal == CHILDID_SELF)
  {
    if (!GetClientRect(accessibility->window, &rect)) return E_FAIL;
  }
  else
  {
    const ReaderViewSemanticNode *node = lectern0_accessibility_node(
      accessibility, child, 0);
    if (!node) return E_INVALIDARG;
    rect.left = node->rect.x;
    rect.top = node->rect.y;
    rect.right = rect.left + node->rect.w;
    rect.bottom = rect.top + node->rect.h;
  }
  POINT origin = {rect.left, rect.top};
  if (!ClientToScreen(accessibility->window, &origin)) return E_FAIL;
  *out_left = origin.x;
  *out_top = origin.y;
  *out_width = rect.right - rect.left;
  *out_height = rect.bottom - rect.top;
  return S_OK;
}

FUNCTION HRESULT STDMETHODCALLTYPE
lectern0_accessibility_navigate(IAccessible *iface, long direction,
                                VARIANT start, VARIANT *out_destination)
{
  if (!out_destination) return E_POINTER;
  VariantInit(out_destination);
  Lectern0Accessibility *accessibility = lectern0_accessibility_from_iface(iface);
  long count = accessibility->app ?
    accessibility->app->reader_view_frame.semantic_node_count : 0;
  long destination = 0;
  if (start.vt != VT_I4) return E_INVALIDARG;
  if (start.lVal == CHILDID_SELF && direction == NAVDIR_FIRSTCHILD && count > 0)
    destination = 1;
  else if (start.lVal == CHILDID_SELF && direction == NAVDIR_LASTCHILD && count > 0)
    destination = count;
  else if (start.lVal > 0 && start.lVal < count && direction == NAVDIR_NEXT)
    destination = start.lVal + 1;
  else if (start.lVal > 1 && start.lVal <= count && direction == NAVDIR_PREVIOUS)
    destination = start.lVal - 1;
  if (!destination) return S_FALSE;
  out_destination->vt = VT_I4;
  out_destination->lVal = destination;
  return S_OK;
}

FUNCTION HRESULT STDMETHODCALLTYPE
lectern0_accessibility_hit_test(IAccessible *iface, long screen_x,
                               long screen_y, VARIANT *out_child)
{
  if (!out_child) return E_POINTER;
  VariantInit(out_child);
  Lectern0Accessibility *accessibility = lectern0_accessibility_from_iface(iface);
  if (!accessibility->app) return S_FALSE;
  POINT point = {screen_x, screen_y};
  if (!ScreenToClient(accessibility->window, &point)) return E_FAIL;
  const ReaderViewFrame *frame = &accessibility->app->reader_view_frame;
  for (UI0S32 index = frame->semantic_node_count - 1; index >= 0; index -= 1)
  {
    const UI0Rect rect = frame->semantic_nodes[index].rect;
    if (point.x >= rect.x && point.y >= rect.y &&
        point.x < rect.x + rect.w && point.y < rect.y + rect.h)
    {
      out_child->vt = VT_I4;
      out_child->lVal = index + 1;
      return S_OK;
    }
  }
  out_child->vt = VT_I4;
  out_child->lVal = CHILDID_SELF;
  return S_OK;
}

FUNCTION HRESULT STDMETHODCALLTYPE
lectern0_accessibility_do_default_action(IAccessible *iface, VARIANT child)
{
  Lectern0Accessibility *accessibility = lectern0_accessibility_from_iface(iface);
  const ReaderViewSemanticNode *node = lectern0_accessibility_node(
    accessibility, child, 0);
  if (!node || !(node->flags & ReaderViewSemantic_Enabled) ||
      !(node->flags & ReaderViewSemantic_Focusable))
    return E_INVALIDARG;
  if (node->role == ReaderViewSemantic_Slider)
  {
    if (!reader_view_accessibility_focus(&accessibility->app->reader_view_state,
                                         node->id))
      return E_FAIL;
    (void)InvalidateRect(accessibility->window, 0, FALSE);
    return S_OK;
  }
  if (!reader_view_accessibility_invoke(&accessibility->app->reader_view_state,
                                        node->id))
    return E_FAIL;
  (void)InvalidateRect(accessibility->window, 0, FALSE);
  return S_OK;
}

FUNCTION HRESULT STDMETHODCALLTYPE
lectern0_accessibility_put_text(IAccessible *iface, VARIANT child, BSTR value)
{
  (void)iface; (void)child; (void)value;
  return E_ACCESSDENIED;
}

static const IAccessibleVtbl lectern0_accessibility_vtable =
{
  lectern0_accessibility_query_interface,
  lectern0_accessibility_add_ref,
  lectern0_accessibility_release,
  lectern0_accessibility_get_type_info_count,
  lectern0_accessibility_get_type_info,
  lectern0_accessibility_get_ids_of_names,
  lectern0_accessibility_invoke,
  lectern0_accessibility_get_parent,
  lectern0_accessibility_get_child_count,
  lectern0_accessibility_get_child,
  lectern0_accessibility_get_name,
  lectern0_accessibility_get_value,
  lectern0_accessibility_no_text,
  lectern0_accessibility_get_role,
  lectern0_accessibility_get_state,
  lectern0_accessibility_no_text,
  lectern0_accessibility_get_help_topic,
  lectern0_accessibility_no_text,
  lectern0_accessibility_get_focus,
  lectern0_accessibility_get_selection,
  lectern0_accessibility_get_default_action,
  lectern0_accessibility_select,
  lectern0_accessibility_location,
  lectern0_accessibility_navigate,
  lectern0_accessibility_hit_test,
  lectern0_accessibility_do_default_action,
  lectern0_accessibility_put_text,
  lectern0_accessibility_put_text,
};

B32
lectern0_accessibility_create(HWND window,
                              Lectern0App *app,
                              Lectern0Accessibility **out_accessibility)
{
  if (!window || !app || !out_accessibility) return 0;
  *out_accessibility = 0;
  Lectern0Accessibility *accessibility =
    (Lectern0Accessibility *)calloc(1, sizeof(*accessibility));
  if (!accessibility) return 0;
  accessibility->iface.lpVtbl = (IAccessibleVtbl *)&lectern0_accessibility_vtable;
  accessibility->reference_count = 1;
  accessibility->window = window;
  accessibility->app = app;
  *out_accessibility = accessibility;
  return 1;
}

void
lectern0_accessibility_destroy(Lectern0Accessibility *accessibility)
{
  if (!accessibility) return;
  Lectern0App *app = accessibility->app;
  accessibility->app = 0;
  accessibility->window = 0;
  if (app && app->accessibility == accessibility) app->accessibility = 0;
  (void)lectern0_accessibility_release(&accessibility->iface);
}

LRESULT
lectern0_accessibility_get_object(Lectern0Accessibility *accessibility,
                                  WPARAM w_param,
                                  LPARAM l_param)
{
  if (!accessibility || (LONG)l_param != OBJID_CLIENT) return 0;
  return LresultFromObject(&IID_IAccessible,
                           w_param,
                           (IUnknown *)&accessibility->iface);
}

void
lectern0_accessibility_publish_frame(Lectern0Accessibility *accessibility,
                                     const ReaderViewFrame *frame)
{
  if (!accessibility || !accessibility->window || !frame) return;
  U64 hash = 1469598103934665603ull;
  UI0ID focused_id = 0;
  long focused_child = 0;
  for (UI0S32 index = 0; index < frame->semantic_node_count; index += 1)
  {
    const ReaderViewSemanticNode *node = frame->semantic_nodes + index;
    hash ^= node->id; hash *= 1099511628211ull;
    hash ^= node->parent_id; hash *= 1099511628211ull;
    hash ^= (U64)node->role; hash *= 1099511628211ull;
    hash ^= node->flags; hash *= 1099511628211ull;
    if (node->flags & ReaderViewSemantic_Focused)
    {
      focused_id = node->id;
      focused_child = index + 1;
    }
  }
  if (hash != accessibility->semantic_hash)
  {
    accessibility->semantic_hash = hash;
    NotifyWinEvent(EVENT_OBJECT_REORDER, accessibility->window,
                   OBJID_CLIENT, CHILDID_SELF);
  }
  if (focused_id && focused_id != accessibility->focused_id)
  {
    accessibility->focused_id = focused_id;
    NotifyWinEvent(EVENT_OBJECT_FOCUS, accessibility->window,
                   OBJID_CLIENT, focused_child);
  }
}
