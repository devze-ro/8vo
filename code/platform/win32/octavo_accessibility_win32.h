#ifndef OCTAVO_ACCESSIBILITY_WIN32_H
#define OCTAVO_ACCESSIBILITY_WIN32_H

struct OctavoApp;

typedef struct OctavoAccessibility OctavoAccessibility;

B32 octavo_accessibility_create(HWND window,
                                  struct OctavoApp *app,
                                  OctavoAccessibility **out_accessibility);
void octavo_accessibility_destroy(OctavoAccessibility *accessibility);
LRESULT octavo_accessibility_get_object(OctavoAccessibility *accessibility,
                                          WPARAM w_param,
                                          LPARAM l_param);
void octavo_accessibility_publish_frame(OctavoAccessibility *accessibility,
                                          const ReaderViewFrame *frame);
B32 octavo_accessibility_enabled_menu_item_contract(
  const ReaderViewSemanticNode *node,
  ReaderViewText expected_name);
long octavo_accessibility_shared_child_id(
  const OctavoAccessibility *accessibility,
  long shared_index);
long octavo_accessibility_host_child_id(
  const OctavoAccessibility *accessibility,
  long host_index);

#endif /* OCTAVO_ACCESSIBILITY_WIN32_H */
