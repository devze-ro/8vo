#ifndef EIGHTVO_ACCESSIBILITY_WIN32_H
#define EIGHTVO_ACCESSIBILITY_WIN32_H

struct EightvoApp;

typedef struct EightvoAccessibility EightvoAccessibility;

B32 eightvo_accessibility_create(HWND window,
                                  struct EightvoApp *app,
                                  EightvoAccessibility **out_accessibility);
void eightvo_accessibility_destroy(EightvoAccessibility *accessibility);
LRESULT eightvo_accessibility_get_object(EightvoAccessibility *accessibility,
                                          WPARAM w_param,
                                          LPARAM l_param);
void eightvo_accessibility_publish_frame(EightvoAccessibility *accessibility,
                                          const ReaderViewFrame *frame);
long eightvo_accessibility_shared_child_id(
  const EightvoAccessibility *accessibility,
  long shared_index);
long eightvo_accessibility_host_child_id(
  const EightvoAccessibility *accessibility,
  long host_index);

#endif /* EIGHTVO_ACCESSIBILITY_WIN32_H */
