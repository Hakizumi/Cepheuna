#include "list_select_tui_util.h"

#include <deque>
#include <ftxui/component/component.hpp>
#include <ftxui/component/component_base.hpp>
#include <ftxui/component/screen_interactive.hpp>

using namespace ftxui;

std::vector<std::string> multipleChoicesTui(const std::vector<std::string>& items)
{
    std::deque<bool> checked(items.size(), false);

    Components boxes;
    for (size_t i = 0; i < items.size(); ++i) {
        boxes.push_back(Checkbox(items[i], &checked[i]));
    }

    const auto container = Container::Vertical(boxes);

    const auto renderer = Renderer(container, [&] {
        Elements elements;
        elements.push_back(text("Use Up/Down to move, Space to toggle, Enter to confirm"));
        elements.push_back(separator());
        for (const auto& c : boxes) {
            elements.push_back(c->Render());
        }
        return vbox(std::move(elements)) | border;
    });

    auto screen = ScreenInteractive::FitComponent();

    const auto app = CatchEvent(renderer, [&](const Event& event) {
        if (event.is_mouse()) {
            return true;
        }
        if (event == Event::Return) {
            screen.ExitLoopClosure()();
            return true;
        }
        return false;
    });

    screen.Loop(app);

    std::vector<std::string> result;

    for (size_t i = 0; i < items.size(); ++i) {
        if (checked[i]) {
            result.push_back(items[i]);
        }
    }

    return result;
}

std::string singleChoicesTui(const std::vector<std::string>& items)
{
    int selected = 0;
    std::string result;

    const auto menu = Menu(&items, &selected);

    const auto renderer = Renderer(menu, [&] {
        return vbox({
                   text("Use Up/Down to move, Enter to confirm"),
                   separator(),
                   menu->Render(),
               }) |
               border;
    });

    auto screen = ScreenInteractive::FitComponent();

    const auto app = CatchEvent(renderer, [&](const Event& event) {
        if (event.is_mouse()) {
            return true;
        }
        if (event == Event::Return) {
            result = items[selected];
            screen.ExitLoopClosure()();
            return true;
        }
        return false;
    });

    screen.Loop(app);

    return result;
}
