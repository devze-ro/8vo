#include <oleacc.h>

struct OctavoAccessibility
{
  IAccessible iface;
  volatile LONG reference_count;
  HWND window;
  OctavoApp *app;
  U64 semantic_hash;
  UI0ID focused_id;
};

FUNCTION OctavoAccessibility *
octavo_accessibility_from_iface(IAccessible *iface)
{
  return CONTAINING_RECORD(iface, OctavoAccessibility, iface);
}

FUNCTION long
octavo_accessibility_child_count(const OctavoAccessibility *accessibility)
{
  if (!accessibility || !accessibility->app) return 0;
  return (long)accessibility->app->reader_view_frame.semantic_node_count +
         (long)accessibility->app->host_control_count;
}

FUNCTION long
octavo_accessibility_host_insertion_shared_count(
  const OctavoAccessibility *accessibility)
{
  if (!accessibility || !accessibility->app) return 0;
  const ReaderViewFrame *frame = &accessibility->app->reader_view_frame;
  for (UI0S32 index = 0; index < frame->semantic_node_count; index += 1)
    if (frame->semantic_nodes[index].control == ReaderViewSemanticControl_Find)
      return (long)index + 1;
  return (long)frame->semantic_node_count;
}

FUNCTION B32
octavo_accessibility_resolve_child(
  const OctavoAccessibility *accessibility,
  long child_id,
  long *out_shared_index,
  long *out_host_index)
{
  if (out_shared_index) *out_shared_index = -1;
  if (out_host_index) *out_host_index = -1;
  if (!accessibility || !accessibility->app || child_id <= CHILDID_SELF)
    return 0;
  long logical_index = child_id - 1;
  long shared_count =
    (long)accessibility->app->reader_view_frame.semantic_node_count;
  long host_count = (long)accessibility->app->host_control_count;
  long insertion =
    octavo_accessibility_host_insertion_shared_count(accessibility);
  if (logical_index < insertion)
  {
    if (logical_index >= shared_count) return 0;
    if (out_shared_index) *out_shared_index = logical_index;
    return 1;
  }
  if (logical_index < insertion + host_count)
  {
    if (out_host_index) *out_host_index = logical_index - insertion;
    return 1;
  }
  long shared_index = logical_index - host_count;
  if (shared_index < 0 || shared_index >= shared_count) return 0;
  if (out_shared_index) *out_shared_index = shared_index;
  return 1;
}

long
octavo_accessibility_shared_child_id(
  const OctavoAccessibility *accessibility,
  long shared_index)
{
  if (!accessibility || !accessibility->app || shared_index < 0 ||
      shared_index >=
        (long)accessibility->app->reader_view_frame.semantic_node_count)
    return 0;
  long insertion =
    octavo_accessibility_host_insertion_shared_count(accessibility);
  long host_count = (long)accessibility->app->host_control_count;
  return shared_index + (shared_index < insertion ? 1 : host_count + 1);
}

long
octavo_accessibility_host_child_id(
  const OctavoAccessibility *accessibility,
  long host_index)
{
  if (!accessibility || !accessibility->app || host_index < 0 ||
      host_index >= (long)accessibility->app->host_control_count)
    return 0;
  return octavo_accessibility_host_insertion_shared_count(accessibility) +
         host_index + 1;
}

FUNCTION OctavoHostControlIdentity
octavo_accessibility_host_identity(const OctavoAccessibility *accessibility,
                                     VARIANT child)
{
  if (!accessibility || !accessibility->app || child.vt != VT_I4 ||
      child.lVal <= CHILDID_SELF)
    return OctavoHostControl_None;
  long host_index = -1;
  if (!octavo_accessibility_resolve_child(accessibility, child.lVal,
                                             0, &host_index))
    return OctavoHostControl_None;
  if (host_index < 0 || host_index >= (long)accessibility->app->host_control_count)
    return OctavoHostControl_None;
  return accessibility->app->host_controls[host_index].identity;
}

FUNCTION const ReaderViewSemanticNode *
octavo_accessibility_node(const OctavoAccessibility *accessibility,
                            VARIANT child,
                            long *out_child_id)
{
  if (!accessibility || !accessibility->app || child.vt != VT_I4 ||
      child.lVal <= CHILDID_SELF)
    return 0;
  const ReaderViewFrame *frame = &accessibility->app->reader_view_frame;
  long shared_index = -1;
  long host_index = -1;
  if (!octavo_accessibility_resolve_child(accessibility, child.lVal,
                                             &shared_index, &host_index))
    return 0;
  if (shared_index >= 0)
  {
    if (!frame->semantic_nodes) return 0;
    if (out_child_id) *out_child_id = child.lVal;
    return frame->semantic_nodes + shared_index;
  }
  if (host_index < 0 ||
      host_index >= (long)accessibility->app->host_control_count)
    return 0;
  if (out_child_id) *out_child_id = child.lVal;
  return &accessibility->app->host_controls[host_index].semantic;
}

FUNCTION BSTR
octavo_accessibility_bstr(ReaderViewText text)
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
octavo_accessibility_role(ReaderViewSemanticRole role)
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
    case ReaderViewSemantic_TextBox: return ROLE_SYSTEM_TEXT;
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
octavo_accessibility_state(const ReaderViewSemanticNode *node)
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

B32
octavo_accessibility_enabled_menu_item_contract(
  const ReaderViewSemanticNode *node,
  ReaderViewText expected_name)
{
  if (!node || !expected_name.data || expected_name.size <= 0 ||
      !node->name.data || node->name.size != expected_name.size ||
      memcmp(node->name.data, expected_name.data,
             (size_t)expected_name.size) != 0)
  {
    return 0;
  }
  long state = octavo_accessibility_state(node);
  return octavo_accessibility_role(node->role) == ROLE_SYSTEM_MENUITEM &&
    (state & STATE_SYSTEM_FOCUSABLE) != 0 &&
    (state & STATE_SYSTEM_UNAVAILABLE) == 0;
}

FUNCTION HRESULT STDMETHODCALLTYPE
octavo_accessibility_query_interface(IAccessible *iface,
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
    &octavo_accessibility_from_iface(iface)->reference_count);
  return S_OK;
}

FUNCTION ULONG STDMETHODCALLTYPE
octavo_accessibility_add_ref(IAccessible *iface)
{
  return (ULONG)InterlockedIncrement(
    &octavo_accessibility_from_iface(iface)->reference_count);
}

FUNCTION ULONG STDMETHODCALLTYPE
octavo_accessibility_release(IAccessible *iface)
{
  OctavoAccessibility *accessibility = octavo_accessibility_from_iface(iface);
  LONG remaining = InterlockedDecrement(&accessibility->reference_count);
  if (remaining == 0) free(accessibility);
  return (ULONG)remaining;
}

FUNCTION HRESULT STDMETHODCALLTYPE
octavo_accessibility_get_type_info_count(IAccessible *iface, UINT *out_count)
{
  (void)iface;
  if (!out_count) return E_POINTER;
  *out_count = 0;
  return S_OK;
}

FUNCTION HRESULT STDMETHODCALLTYPE
octavo_accessibility_get_type_info(IAccessible *iface, UINT index,
                                     LCID locale, ITypeInfo **out_info)
{
  (void)iface; (void)index; (void)locale;
  if (out_info) *out_info = 0;
  return E_NOTIMPL;
}

FUNCTION HRESULT STDMETHODCALLTYPE
octavo_accessibility_get_ids_of_names(IAccessible *iface, REFIID riid,
                                        LPOLESTR *names, UINT name_count,
                                        LCID locale, DISPID *out_ids)
{
  (void)iface; (void)riid; (void)names; (void)name_count;
  (void)locale; (void)out_ids;
  return DISP_E_UNKNOWNNAME;
}

FUNCTION HRESULT STDMETHODCALLTYPE
octavo_accessibility_invoke(IAccessible *iface, DISPID member, REFIID riid,
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
octavo_accessibility_get_parent(IAccessible *iface, IDispatch **out_parent)
{
  if (!out_parent) return E_POINTER;
  *out_parent = 0;
  OctavoAccessibility *accessibility = octavo_accessibility_from_iface(iface);
  IAccessible *parent = 0;
  HRESULT result = AccessibleObjectFromWindow(accessibility->window,
                                               OBJID_WINDOW,
                                               &IID_IAccessible,
                                               (void **)&parent);
  if (SUCCEEDED(result)) *out_parent = (IDispatch *)parent;
  return result;
}

FUNCTION HRESULT STDMETHODCALLTYPE
octavo_accessibility_get_child_count(IAccessible *iface, long *out_count)
{
  if (!out_count) return E_POINTER;
  OctavoAccessibility *accessibility = octavo_accessibility_from_iface(iface);
  *out_count = octavo_accessibility_child_count(accessibility);
  return S_OK;
}

FUNCTION HRESULT STDMETHODCALLTYPE
octavo_accessibility_get_child(IAccessible *iface, VARIANT child,
                                 IDispatch **out_child)
{
  if (!out_child) return E_POINTER;
  *out_child = 0;
  return octavo_accessibility_node(
    octavo_accessibility_from_iface(iface), child, 0) ? S_FALSE : E_INVALIDARG;
}

FUNCTION HRESULT STDMETHODCALLTYPE
octavo_accessibility_get_name(IAccessible *iface, VARIANT child,
                                BSTR *out_name)
{
  if (!out_name) return E_POINTER;
  *out_name = 0;
  if (child.vt == VT_I4 && child.lVal == CHILDID_SELF)
  {
    OctavoAccessibility *accessibility =
      octavo_accessibility_from_iface(iface);
    *out_name = SysAllocString(accessibility->app &&
                               octavo_library_active(accessibility->app) ?
                               L"8vo Library" :
                               L"8vo reader");
    return *out_name ? S_OK : E_OUTOFMEMORY;
  }
  const ReaderViewSemanticNode *node = octavo_accessibility_node(
    octavo_accessibility_from_iface(iface), child, 0);
  if (!node) return E_INVALIDARG;
  *out_name = octavo_accessibility_bstr(node->name);
  return *out_name ? S_OK : E_OUTOFMEMORY;
}

FUNCTION HRESULT STDMETHODCALLTYPE
octavo_accessibility_get_value(IAccessible *iface, VARIANT child,
                                 BSTR *out_value)
{
  if (!out_value) return E_POINTER;
  *out_value = 0;
  const ReaderViewSemanticNode *node = octavo_accessibility_node(
    octavo_accessibility_from_iface(iface), child, 0);
  if (!node) return child.vt == VT_I4 && child.lVal == CHILDID_SELF ? S_FALSE : E_INVALIDARG;
  if (node->value.data && node->value.size > 0)
  {
    *out_value = octavo_accessibility_bstr(node->value);
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
octavo_accessibility_no_text(IAccessible *iface, VARIANT child,
                               BSTR *out_text)
{
  (void)iface; (void)child;
  if (!out_text) return E_POINTER;
  *out_text = 0;
  return S_FALSE;
}

FUNCTION HRESULT STDMETHODCALLTYPE
octavo_accessibility_get_role(IAccessible *iface, VARIANT child,
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
  const ReaderViewSemanticNode *node = octavo_accessibility_node(
    octavo_accessibility_from_iface(iface), child, 0);
  if (!node) return E_INVALIDARG;
  out_role->lVal = octavo_accessibility_role(node->role);
  return S_OK;
}

FUNCTION HRESULT STDMETHODCALLTYPE
octavo_accessibility_get_state(IAccessible *iface, VARIANT child,
                                 VARIANT *out_state)
{
  if (!out_state) return E_POINTER;
  VariantInit(out_state);
  out_state->vt = VT_I4;
  if (child.vt == VT_I4 && child.lVal == CHILDID_SELF) return S_OK;
  const ReaderViewSemanticNode *node = octavo_accessibility_node(
    octavo_accessibility_from_iface(iface), child, 0);
  if (!node) return E_INVALIDARG;
  out_state->lVal = octavo_accessibility_state(node);
  return S_OK;
}

FUNCTION HRESULT STDMETHODCALLTYPE
octavo_accessibility_get_help_topic(IAccessible *iface, BSTR *out_file,
                                      VARIANT child, long *out_topic)
{
  (void)iface; (void)child;
  if (!out_file || !out_topic) return E_POINTER;
  *out_file = 0;
  *out_topic = 0;
  return S_FALSE;
}

FUNCTION HRESULT STDMETHODCALLTYPE
octavo_accessibility_get_focus(IAccessible *iface, VARIANT *out_child)
{
  if (!out_child) return E_POINTER;
  VariantInit(out_child);
  OctavoAccessibility *accessibility = octavo_accessibility_from_iface(iface);
  if (!accessibility->app) return S_FALSE;
  const ReaderViewFrame *frame = &accessibility->app->reader_view_frame;
  for (U32 index = 0; index < accessibility->app->host_control_count; index += 1)
  {
    if (accessibility->app->host_controls[index].semantic.flags &
        ReaderViewSemantic_Focused)
    {
      out_child->vt = VT_I4;
      out_child->lVal =
        octavo_accessibility_host_child_id(accessibility, (long)index);
      return S_OK;
    }
  }
  for (UI0S32 index = 0; index < frame->semantic_node_count; index += 1)
  {
    if (frame->semantic_nodes[index].flags & ReaderViewSemantic_Focused)
    {
      out_child->vt = VT_I4;
      out_child->lVal =
        octavo_accessibility_shared_child_id(accessibility, (long)index);
      return S_OK;
    }
  }
  return S_FALSE;
}

FUNCTION HRESULT STDMETHODCALLTYPE
octavo_accessibility_get_selection(IAccessible *iface, VARIANT *out_child)
{
  if (!out_child) return E_POINTER;
  VariantInit(out_child);
  OctavoAccessibility *accessibility = octavo_accessibility_from_iface(iface);
  if (!accessibility->app) return S_FALSE;
  const ReaderViewFrame *frame = &accessibility->app->reader_view_frame;
  for (UI0S32 index = 0; index < frame->semantic_node_count; index += 1)
  {
    if (frame->semantic_nodes[index].flags & ReaderViewSemantic_Selected)
    {
      out_child->vt = VT_I4;
      out_child->lVal =
        octavo_accessibility_shared_child_id(accessibility, (long)index);
      return S_OK;
    }
  }
  return S_FALSE;
}

FUNCTION HRESULT STDMETHODCALLTYPE
octavo_accessibility_get_default_action(IAccessible *iface, VARIANT child,
                                          BSTR *out_action)
{
  if (!out_action) return E_POINTER;
  *out_action = 0;
  const ReaderViewSemanticNode *node = octavo_accessibility_node(
    octavo_accessibility_from_iface(iface), child, 0);
  if (!node || !(node->flags & ReaderViewSemantic_Focusable))
    return node ? S_FALSE : E_INVALIDARG;
  const wchar_t *label = L"Activate";
  if (node->role == ReaderViewSemantic_SearchBox ||
      node->role == ReaderViewSemantic_TextArea ||
      node->role == ReaderViewSemantic_TextBox) label = L"Edit";
  else if (node->role == ReaderViewSemantic_Slider) label = L"Focus";
  *out_action = SysAllocString(label);
  return *out_action ? S_OK : E_OUTOFMEMORY;
}

FUNCTION HRESULT STDMETHODCALLTYPE
octavo_accessibility_select(IAccessible *iface, long flags, VARIANT child)
{
  OctavoAccessibility *accessibility = octavo_accessibility_from_iface(iface);
  const ReaderViewSemanticNode *node = octavo_accessibility_node(
    accessibility, child, 0);
  if (!node || !(node->flags & ReaderViewSemantic_Focusable))
    return E_INVALIDARG;
  if (flags & (SELFLAG_TAKEFOCUS | SELFLAG_TAKESELECTION))
  {
    (void)SetFocus(accessibility->window);
    OctavoHostControlIdentity host_identity =
      octavo_accessibility_host_identity(accessibility, child);
    if (host_identity != OctavoHostControl_None)
    {
      if (!octavo_host_focus_set(accessibility->app, host_identity, 1))
        return E_FAIL;
      (void)InvalidateRect(accessibility->window, 0, FALSE);
      return S_OK;
    }
    (void)octavo_host_focus_set(accessibility->app,
                                  OctavoHostControl_None,
                                  0);
    if (!reader_view_accessibility_focus(&accessibility->app->reader_view_state,
                                         node->id))
      return E_FAIL;
    (void)InvalidateRect(accessibility->window, 0, FALSE);
    return S_OK;
  }
  return S_FALSE;
}

FUNCTION HRESULT STDMETHODCALLTYPE
octavo_accessibility_location(IAccessible *iface,
                                long *out_left, long *out_top,
                                long *out_width, long *out_height,
                                VARIANT child)
{
  if (!out_left || !out_top || !out_width || !out_height) return E_POINTER;
  OctavoAccessibility *accessibility = octavo_accessibility_from_iface(iface);
  RECT rect = {0};
  if (child.vt == VT_I4 && child.lVal == CHILDID_SELF)
  {
    if (!GetClientRect(accessibility->window, &rect)) return E_FAIL;
  }
  else
  {
    const ReaderViewSemanticNode *node = octavo_accessibility_node(
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
octavo_accessibility_navigate(IAccessible *iface, long direction,
                                VARIANT start, VARIANT *out_destination)
{
  if (!out_destination) return E_POINTER;
  VariantInit(out_destination);
  OctavoAccessibility *accessibility = octavo_accessibility_from_iface(iface);
  long count = octavo_accessibility_child_count(accessibility);
  long candidate = 0;
  long step = 0;
  if (start.vt != VT_I4) return E_INVALIDARG;
  if (start.lVal == CHILDID_SELF && direction == NAVDIR_FIRSTCHILD && count > 0)
  {
    candidate = 1;
    step = 1;
  }
  else if (start.lVal == CHILDID_SELF && direction == NAVDIR_LASTCHILD && count > 0)
  {
    candidate = count;
    step = -1;
  }
  else if (start.lVal > 0 && start.lVal < count && direction == NAVDIR_NEXT)
  {
    candidate = start.lVal + 1;
    step = 1;
  }
  else if (start.lVal > 1 && start.lVal <= count && direction == NAVDIR_PREVIOUS)
  {
    candidate = start.lVal - 1;
    step = -1;
  }
  while (candidate > 0 && candidate <= count)
  {
    VARIANT child;
    VariantInit(&child);
    child.vt = VT_I4;
    child.lVal = candidate;
    const ReaderViewSemanticNode *node =
      octavo_accessibility_node(accessibility, child, 0);
    if (node &&
        (node->flags & (ReaderViewSemantic_Enabled |
                        ReaderViewSemantic_Focusable)) ==
          (ReaderViewSemantic_Enabled | ReaderViewSemantic_Focusable))
    {
      out_destination->vt = VT_I4;
      out_destination->lVal = candidate;
      return S_OK;
    }
    candidate += step;
  }
  return S_FALSE;
}

FUNCTION HRESULT STDMETHODCALLTYPE
octavo_accessibility_hit_test(IAccessible *iface, long screen_x,
                               long screen_y, VARIANT *out_child)
{
  if (!out_child) return E_POINTER;
  VariantInit(out_child);
  OctavoAccessibility *accessibility = octavo_accessibility_from_iface(iface);
  if (!accessibility->app) return S_FALSE;
  POINT point = {screen_x, screen_y};
  if (!ScreenToClient(accessibility->window, &point)) return E_FAIL;
  long count = octavo_accessibility_child_count(accessibility);
  for (long index = count - 1; index >= 0; index -= 1)
  {
    VARIANT child;
    VariantInit(&child);
    child.vt = VT_I4;
    child.lVal = index + 1;
    const ReaderViewSemanticNode *node =
      octavo_accessibility_node(accessibility, child, 0);
    if (!node) continue;
    const UI0Rect rect = node->rect;
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
octavo_accessibility_do_default_action(IAccessible *iface, VARIANT child)
{
  OctavoAccessibility *accessibility = octavo_accessibility_from_iface(iface);
  const ReaderViewSemanticNode *node = octavo_accessibility_node(
    accessibility, child, 0);
  if (!node || !(node->flags & ReaderViewSemantic_Enabled) ||
      !(node->flags & ReaderViewSemantic_Focusable))
    return E_INVALIDARG;
  OctavoHostControlIdentity host_identity =
    octavo_accessibility_host_identity(accessibility, child);
  if (host_identity != OctavoHostControl_None)
  {
    if (!octavo_host_focus_set(accessibility->app, host_identity, 1) ||
        !octavo_host_control_invoke(accessibility->app, host_identity))
      return E_FAIL;
    (void)InvalidateRect(accessibility->window, 0, FALSE);
    return S_OK;
  }
  (void)octavo_host_focus_set(accessibility->app,
                                OctavoHostControl_None,
                                0);
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
octavo_accessibility_put_text(IAccessible *iface, VARIANT child, BSTR value)
{
  (void)iface; (void)child; (void)value;
  return E_ACCESSDENIED;
}

static const IAccessibleVtbl octavo_accessibility_vtable =
{
  octavo_accessibility_query_interface,
  octavo_accessibility_add_ref,
  octavo_accessibility_release,
  octavo_accessibility_get_type_info_count,
  octavo_accessibility_get_type_info,
  octavo_accessibility_get_ids_of_names,
  octavo_accessibility_invoke,
  octavo_accessibility_get_parent,
  octavo_accessibility_get_child_count,
  octavo_accessibility_get_child,
  octavo_accessibility_get_name,
  octavo_accessibility_get_value,
  octavo_accessibility_no_text,
  octavo_accessibility_get_role,
  octavo_accessibility_get_state,
  octavo_accessibility_no_text,
  octavo_accessibility_get_help_topic,
  octavo_accessibility_no_text,
  octavo_accessibility_get_focus,
  octavo_accessibility_get_selection,
  octavo_accessibility_get_default_action,
  octavo_accessibility_select,
  octavo_accessibility_location,
  octavo_accessibility_navigate,
  octavo_accessibility_hit_test,
  octavo_accessibility_do_default_action,
  octavo_accessibility_put_text,
  octavo_accessibility_put_text,
};

B32
octavo_accessibility_create(HWND window,
                              OctavoApp *app,
                              OctavoAccessibility **out_accessibility)
{
  if (!window || !app || !out_accessibility) return 0;
  *out_accessibility = 0;
  OctavoAccessibility *accessibility =
    (OctavoAccessibility *)calloc(1, sizeof(*accessibility));
  if (!accessibility) return 0;
  accessibility->iface.lpVtbl = (IAccessibleVtbl *)&octavo_accessibility_vtable;
  accessibility->reference_count = 1;
  accessibility->window = window;
  accessibility->app = app;
  *out_accessibility = accessibility;
  return 1;
}

void
octavo_accessibility_destroy(OctavoAccessibility *accessibility)
{
  if (!accessibility) return;
  OctavoApp *app = accessibility->app;
  accessibility->app = 0;
  accessibility->window = 0;
  if (app && app->accessibility == accessibility) app->accessibility = 0;
  (void)octavo_accessibility_release(&accessibility->iface);
}

LRESULT
octavo_accessibility_get_object(OctavoAccessibility *accessibility,
                                  WPARAM w_param,
                                  LPARAM l_param)
{
  if (!accessibility || (LONG)l_param != OBJID_CLIENT) return 0;
  return LresultFromObject(&IID_IAccessible,
                           w_param,
                           (IUnknown *)&accessibility->iface);
}

void
octavo_accessibility_publish_frame(OctavoAccessibility *accessibility,
                                     const ReaderViewFrame *frame)
{
  if (!accessibility || !accessibility->window || !frame) return;
  U64 hash = 1469598103934665603ull;
  UI0ID focused_id = 0;
  long focused_child = 0;
  long count = octavo_accessibility_child_count(accessibility);
  for (long index = 0; index < count; index += 1)
  {
    VARIANT child;
    VariantInit(&child);
    child.vt = VT_I4;
    child.lVal = index + 1;
    const ReaderViewSemanticNode *node =
      octavo_accessibility_node(accessibility, child, 0);
    if (!node) continue;
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
  if (focused_id != accessibility->focused_id)
  {
    accessibility->focused_id = focused_id;
    if (focused_id)
    {
      NotifyWinEvent(EVENT_OBJECT_FOCUS, accessibility->window,
                     OBJID_CLIENT, focused_child);
    }
  }
}
