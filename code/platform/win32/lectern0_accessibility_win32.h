#ifndef LECTERN0_ACCESSIBILITY_WIN32_H
#define LECTERN0_ACCESSIBILITY_WIN32_H

struct Lectern0App;

typedef struct Lectern0Accessibility Lectern0Accessibility;

B32 lectern0_accessibility_create(HWND window,
                                  struct Lectern0App *app,
                                  Lectern0Accessibility **out_accessibility);
void lectern0_accessibility_destroy(Lectern0Accessibility *accessibility);
LRESULT lectern0_accessibility_get_object(Lectern0Accessibility *accessibility,
                                          WPARAM w_param,
                                          LPARAM l_param);
void lectern0_accessibility_publish_frame(Lectern0Accessibility *accessibility,
                                          const ReaderViewFrame *frame);
long lectern0_accessibility_shared_child_id(
  const Lectern0Accessibility *accessibility,
  long shared_index);
long lectern0_accessibility_host_child_id(
  const Lectern0Accessibility *accessibility,
  long host_index);

#endif /* LECTERN0_ACCESSIBILITY_WIN32_H */
